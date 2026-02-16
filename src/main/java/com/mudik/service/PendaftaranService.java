package com.mudik.service;

import com.mudik.model.PendaftaranMudik;
import com.mudik.model.Rute;
import com.mudik.model.User;
import com.mudik.resource.PendaftaranResource.PendaftaranMultipartForm;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.resteasy.reactive.multipart.FileUpload;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.Period;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class PendaftaranService {

    @ConfigProperty(name = "quarkus.http.body.uploads-directory")
    String uploadDir;

    // =================================================================
    // 1. PROSES PENDAFTARAN WEB (FIX POIN 1, 10, 11)
    // =================================================================
    @Transactional
    public void prosesPendaftaranWeb(User user, Rute rute, PendaftaranMultipartForm form) throws Exception {
        int jumlahPeserta = form.nama_peserta.size();

        // VALIDASI KUOTA RUTE
        if (rute.getSisaKuota() < jumlahPeserta) {
            throw new Exception("Kuota Rute Habis! Sisa tiket: " + rute.getSisaKuota());
        }

        // VALIDASI LIMIT AKUN (Max 6)
        // Hitung cuma yang statusnya AKTIF (Bukan Ditolak/Batal)
        long sudahDaftar = PendaftaranMudik.count("user.user_id = ?1 AND status_pendaftaran NOT IN ('DITOLAK', 'DIBATALKAN')", user.user_id);
        if (sudahDaftar + jumlahPeserta > 6) {
            throw new Exception("Kuota akun penuh! Sisa slot anda: " + (6 - sudahDaftar));
        }

        // VALIDASI NIK DUPLIKAT
        // Cek NIK, tapi abaikan kalau status sebelumnya DITOLAK/DIBATALKAN (Boleh daftar lagi)
        List<String> nikDuplikat = new ArrayList<>();
        if (form.nik_peserta != null) {
            for (String nik : form.nik_peserta) {
                String cleanNik = nik.trim();
                long cek = PendaftaranMudik.count("nik_peserta = ?1 AND status_pendaftaran NOT IN ('DIBATALKAN', 'DITOLAK')", cleanNik);
                if (cek > 0) nikDuplikat.add(cleanNik);
            }
        }
        if (!nikDuplikat.isEmpty()) {
            throw new Exception("NIK berikut sudah terdaftar & aktif: " + String.join(", ", nikDuplikat) + ". Cek riwayat atau hubungi admin.");
        }

        // LOOP SIMPAN PESERTA
        for (int i = 0; i < jumlahPeserta; i++) {
            PendaftaranMudik p = new PendaftaranMudik();
            p.user = user;
            p.rute = rute;
            p.nama_peserta = form.nama_peserta.get(i).toUpperCase();

            String rawNik = (form.nik_peserta != null && i < form.nik_peserta.size()) ? form.nik_peserta.get(i).trim() : "-";
            if (rawNik.length() > 16) rawNik = rawNik.substring(0, 16);
            p.nik_peserta = rawNik;

            // Logic Umur & Kategori
            try {
                if (form.tanggal_lahir != null && i < form.tanggal_lahir.size()) {
                    LocalDate tgl = LocalDate.parse(form.tanggal_lahir.get(i));
                    p.tanggal_lahir = tgl;
                    int umur = Period.between(tgl, LocalDate.now()).getYears();
                    p.kategori_penumpang = (umur < 5) ? "ANAK" : "DEWASA";
                } else {
                    p.tanggal_lahir = LocalDate.now();
                    p.kategori_penumpang = "DEWASA";
                }
            } catch (Exception e) {
                p.tanggal_lahir = LocalDate.now();
                p.kategori_penumpang = "DEWASA";
            }

            p.jenis_kelamin = (form.jenis_kelamin != null && i < form.jenis_kelamin.size()) ? form.jenis_kelamin.get(i) : "L";
            p.alamat_rumah = (form.alamat_rumah != null && i < form.alamat_rumah.size()) ? form.alamat_rumah.get(i) : "-";

            if (form.no_hp_peserta != null && i < form.no_hp_peserta.size()) {
                p.no_hp_peserta = form.no_hp_peserta.get(i);
            } else {
                p.no_hp_peserta = user.no_hp;
            }

            try {
                if (form.fotoBukti != null && i < form.fotoBukti.size()) {
                    FileUpload file = form.fotoBukti.get(i);
                    if (file != null && file.fileName() != null && !file.fileName().isEmpty()) {
                        p.foto_identitas_path = uploadFileHelper(file, p.nik_peserta);
                    }
                }
            } catch (Exception e) {
                System.out.println("Gagal upload foto: " + e.getMessage());
            }

            // STATUS AWAL: MENUNGGU VERIFIKASI
            p.status_pendaftaran = "MENUNGGU VERIFIKASI";
            p.kode_booking = "MDK-" + System.currentTimeMillis() + "-" + (i + 1);

            p.persist();
        }

        // Update Kuota Rute (Berkurang)
        if (rute.kuota_terisi == null) rute.kuota_terisi = 0;
        rute.kuota_terisi += jumlahPeserta;
    }

    // =================================================================
    // 2. PROSES KONFIRMASI KEHADIRAN (BATCH) - FIX STATUS H-3
    // =================================================================
    @Transactional
    public String prosesKonfirmasi(Long userId, List<Long> idsTetapIkut) throws Exception {

        // Logic mencari yang "DITERIMA H-3"
        List<PendaftaranMudik> keluarga = PendaftaranMudik.list(
                "user.user_id = ?1 AND status_pendaftaran = 'DITERIMA H-3'", userId
        );

        if (keluarga.isEmpty()) {
            throw new Exception("Tidak ada data yang perlu dikonfirmasi (Status harus DITERIMA H-3).");
        }

        int countHadir = 0;
        int countBatal = 0;

        for (PendaftaranMudik p : keluarga) {
            if (idsTetapIkut.contains(p.pendaftaran_id)) {
                // User Konfirmasi -> "TERVERIFIKASI/ SIAP BERANGKAT"
                p.status_pendaftaran = "TERVERIFIKASI/ SIAP BERANGKAT";
                countHadir++;
            } else {
                // User Gak Centang -> "DIBATALKAN"
                p.status_pendaftaran = "DIBATALKAN";

                // Balikin Kuota kalau batal
                if (p.rute != null && p.rute.kuota_terisi > 0) {
                    p.rute.kuota_terisi -= 1;
                }
                countBatal++;
            }
            p.persist();
        }
        return countHadir + " Siap Berangkat, " + countBatal + " Dibatalkan.";
    }

    // =================================================================
    // 3. FITUR EDIT / AJUKAN ULANG (FIX POIN 1 & 11)
    // =================================================================
    @Transactional
    public void editPendaftaran(Long userId, Long pendaftaranId, PendaftaranMultipartForm form) throws Exception {
        PendaftaranMudik p = PendaftaranMudik.findById(pendaftaranId);
        if (p == null) throw new Exception("Data tidak ditemukan.");

        if (!p.user.user_id.equals(userId)) throw new Exception("Anda tidak berhak mengedit data ini.");

        // Hanya DITOLAK yang boleh diedit
        if (!"DITOLAK".equals(p.status_pendaftaran)) {
            throw new Exception("Hanya data dengan status DITOLAK yang bisa diperbaiki.");
        }

        // Cek Kuota & Ambil Slot Lagi (Karena pas ditolak kuota udah dibalikin)
        if (p.rute.getSisaKuota() <= 0) {
            throw new Exception("Yah, Kuota Rute ini sudah penuh! Tidak bisa mengajukan ulang.");
        }

        // Update Data (Nama, NIK, dll)
        if (form.nama_peserta != null && !form.nama_peserta.isEmpty()) p.nama_peserta = form.nama_peserta.get(0).toUpperCase();
        if (form.nik_peserta != null && !form.nik_peserta.isEmpty()) p.nik_peserta = form.nik_peserta.get(0);
        if (form.jenis_kelamin != null && !form.jenis_kelamin.isEmpty()) p.jenis_kelamin = form.jenis_kelamin.get(0);
        if (form.alamat_rumah != null && !form.alamat_rumah.isEmpty()) p.alamat_rumah = form.alamat_rumah.get(0);

        try {
            if (form.tanggal_lahir != null && !form.tanggal_lahir.isEmpty()) {
                LocalDate tgl = LocalDate.parse(form.tanggal_lahir.get(0));
                p.tanggal_lahir = tgl;
                p.kategori_penumpang = (Period.between(tgl, LocalDate.now()).getYears() < 5) ? "ANAK" : "DEWASA";
            }
        } catch (Exception e) {}

        try {
            if (form.fotoBukti != null && !form.fotoBukti.isEmpty()) {
                FileUpload file = form.fotoBukti.get(0);
                if (file != null && file.fileName() != null && !file.fileName().isEmpty()) {
                    p.foto_identitas_path = uploadFileHelper(file, p.nik_peserta);
                }
            }
        } catch (Exception e) {}

        // RESET STATUS & AMBIL KUOTA LAGI
        p.status_pendaftaran = "MENUNGGU VERIFIKASI";

        if (p.rute.kuota_terisi == null) p.rute.kuota_terisi = 0;
        p.rute.kuota_terisi += 1;

        p.persist();
    }

    // =================================================================
    // 4. HELPER ADMIN: TOLAK DATA & BALIKIN KUOTA (FIX POIN 2)
    // =================================================================
    @Transactional
    public void adminTolakPeserta(Long pendaftaranId) {
        PendaftaranMudik p = PendaftaranMudik.findById(pendaftaranId);
        if (p == null) return;

        // Cek dulu, kalau statusnya bukan DITOLAK/BATAL, baru kita proses
        if (!"DITOLAK".equals(p.status_pendaftaran) && !"DIBATALKAN".equals(p.status_pendaftaran)) {

            p.status_pendaftaran = "DITOLAK"; // Ubah status jadi DITOLAK

            // INI LOGIC BACKEND: BALIKIN KUOTA KE RUTE
            if (p.rute != null && p.rute.kuota_terisi > 0) {
                p.rute.kuota_terisi = p.rute.kuota_terisi - 1;
            }
            p.persist();
        }
    }

    // HELPER UPLOAD FILE
    private String uploadFileHelper(FileUpload fileUpload, String nik) throws IOException {
        File folder = new File(uploadDir);
        if (!folder.exists()) folder.mkdirs();

        String originalName = fileUpload.fileName();
        String ext = (originalName.contains(".")) ? originalName.substring(originalName.lastIndexOf(".")) : ".jpg";
        String newName = "ktp-" + nik + "-" + UUID.randomUUID().toString().substring(0, 5) + ext;

        File dest = new File(folder, newName);
        Files.move(fileUpload.uploadedFile(), dest.toPath(), StandardCopyOption.REPLACE_EXISTING);
        return "uploads/" + newName;
    }
}