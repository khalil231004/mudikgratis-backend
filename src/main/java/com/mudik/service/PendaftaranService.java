package com.mudik.service;

import com.mudik.model.PendaftaranMudik;
import com.mudik.model.Rute;
import com.mudik.model.User;
import com.mudik.resource.PendaftaranResource.PendaftaranMultipartForm;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.LockModeType;
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

    @Inject
    WhatsAppService whatsAppService;

    // =================================================================
    // 1. PROSES PENDAFTARAN WEB
    // =================================================================
    @Transactional
    public void prosesPendaftaranWeb(User user, Rute rute, PendaftaranMultipartForm form) throws Exception {
        int jumlahPeserta = form.nama_peserta.size();

        // Cek Kuota Awal (Tanpa Lock)
        if (rute.getSisaKuota() < jumlahPeserta) {
            throw new Exception("Kuota Rute Habis! Sisa tiket: " + rute.getSisaKuota());
        }

        // Cek Limit Akun
        long sudahDaftar = PendaftaranMudik.count("user.user_id = ?1 AND status_pendaftaran NOT IN ('DITOLAK', 'DIBATALKAN')", user.user_id);
        if (sudahDaftar + jumlahPeserta > 6) {
            throw new Exception("Kuota akun penuh! Sisa slot anda: " + (6 - sudahDaftar));
        }

        // Cek NIK
        List<String> nikDuplikat = new ArrayList<>();
        if (form.nik_peserta != null) {
            for (String nik : form.nik_peserta) {
                String cleanNik = nik.trim();
                long cek = PendaftaranMudik.count("nik_peserta = ?1 AND status_pendaftaran NOT IN ('DIBATALKAN', 'DITOLAK')", cleanNik);
                if (cek > 0) nikDuplikat.add(cleanNik);
            }
        }
        if (!nikDuplikat.isEmpty()) {
            throw new Exception("NIK berikut sudah terdaftar & aktif: " + String.join(", ", nikDuplikat));
        }

        // Siapkan List
        List<PendaftaranMudik> listToSave = new ArrayList<>();

        for (int i = 0; i < jumlahPeserta; i++) {
            PendaftaranMudik p = new PendaftaranMudik();
            p.user = user;
            p.nama_peserta = form.nama_peserta.get(i).toUpperCase();

            String rawNik = (form.nik_peserta != null && i < form.nik_peserta.size()) ? form.nik_peserta.get(i).trim() : "-";
            if (rawNik.length() > 16) rawNik = rawNik.substring(0, 16);
            p.nik_peserta = rawNik;

            // Logic Umur
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

            String hp = (form.no_hp_peserta != null && i < form.no_hp_peserta.size()) ? form.no_hp_peserta.get(i) : user.no_hp;
            p.no_hp_peserta = hp;

            // Upload File (Sebelum Lock)
            if (form.fotoBukti != null && i < form.fotoBukti.size()) {
                FileUpload file = form.fotoBukti.get(i);
                if (file != null && file.fileName() != null && !file.fileName().isEmpty()) {
                    p.foto_identitas_path = uploadFileHelper(file, p.nik_peserta);
                }
            }

            p.status_pendaftaran = "MENUNGGU VERIFIKASI";
            p.kode_booking = "MDK-" + System.currentTimeMillis() + "-" + (i + 1);

            listToSave.add(p);
        }

        // 🔥 LOCK DB & UPDATE KUOTA TERISI
        Rute ruteLocked = Rute.findById(rute.rute_id, LockModeType.PESSIMISTIC_WRITE);

        if (ruteLocked.getSisaKuota() < jumlahPeserta) {
            throw new Exception("Mohon maaf, Kuota Rute baru saja habis!");
        }

        for (PendaftaranMudik p : listToSave) {
            p.rute = ruteLocked;
            p.persist();
        }

        // Tambah Kuota Terisi
        if (ruteLocked.kuota_terisi == null) ruteLocked.kuota_terisi = 0;
        ruteLocked.kuota_terisi += jumlahPeserta;

        // Pastikan tidak menyentuh kuota_total
        ruteLocked.persist();
    }

    // =================================================================
    // 2. PROSES KONFIRMASI KEHADIRAN (BATCH)
    // =================================================================
    @Transactional
    public String prosesKonfirmasi(Long userId, List<Long> idsTetapIkut) throws Exception {
        List<PendaftaranMudik> keluarga = PendaftaranMudik.list("user.user_id = ?1 AND status_pendaftaran = 'DITERIMA H-3'", userId);
        if (keluarga.isEmpty()) throw new Exception("Tidak ada data DITERIMA H-3.");

        int countHadir = 0; int countBatal = 0;

        // Ambil Rute sekali saja untuk lock
        Rute ruteLocked = null;
        if (!keluarga.isEmpty()) {
            ruteLocked = Rute.findById(keluarga.get(0).rute.rute_id, LockModeType.PESSIMISTIC_WRITE);
        }

        for (PendaftaranMudik p : keluarga) {
            if (idsTetapIkut.contains(p.pendaftaran_id)) {
                // Konfirmasi: Masuk Bus (Kuota Fix nanti diatur saat assign bus, di sini cuma status)
                p.status_pendaftaran = "TERVERIFIKASI/ SIAP BERANGKAT";
                countHadir++;
            } else {
                // Batal: Kurangi Kuota Terisi
                p.status_pendaftaran = "DIBATALKAN";

                if (ruteLocked != null && ruteLocked.kuota_terisi > 0) {
                    ruteLocked.kuota_terisi -= 1;
                }
                countBatal++;
            }
            p.persist();
        }

        return countHadir + " Siap Berangkat, " + countBatal + " Dibatalkan.";
    }

    // =================================================================
    // 3. EDIT PENDAFTARAN
    // =================================================================
    @Transactional
    public void editPendaftaran(Long userId, Long pendaftaranId, PendaftaranMultipartForm form) throws Exception {
        PendaftaranMudik p = PendaftaranMudik.findById(pendaftaranId);
        if (p == null || !p.user.user_id.equals(userId)) throw new Exception("Data tidak valid.");

        if (!"DITOLAK".equals(p.status_pendaftaran)) throw new Exception("Hanya status DITOLAK yang bisa diperbaiki.");

        if (form.nama_peserta != null) p.nama_peserta = form.nama_peserta.get(0).toUpperCase();
        if (form.nik_peserta != null) p.nik_peserta = form.nik_peserta.get(0);

        if (form.fotoBukti != null && !form.fotoBukti.isEmpty()) {
            FileUpload file = form.fotoBukti.get(0);
            if (file != null && file.fileName() != null && !file.fileName().isEmpty() && file.size() > 0) {
                p.foto_identitas_path = uploadFileHelper(file, p.nik_peserta);
            }
        }

        // Ambil ulang kuota karena sebelumnya sudah dibalikin saat DITOLAK
        Rute r = Rute.findById(p.rute.rute_id, LockModeType.PESSIMISTIC_WRITE);
        if (r.getSisaKuota() <= 0) throw new Exception("Kuota Rute Penuh! Tidak bisa mengajukan ulang.");

        p.status_pendaftaran = "MENUNGGU VERIFIKASI";
        p.alasan_tolak = null;

        // Tambah Kuota Terisi
        if (r.kuota_terisi == null) r.kuota_terisi = 0;
        r.kuota_terisi += 1;

        p.persist();
    }

    // =================================================================
    // 4. ADMIN: TOLAK PESERTA SATUAN
    // =================================================================
    @Transactional
    public void adminTolakPeserta(Long pendaftaranId) {
        PendaftaranMudik p = PendaftaranMudik.findById(pendaftaranId);
        if (p == null) return;

        if (!"DITOLAK".equals(p.status_pendaftaran) && !"DIBATALKAN".equals(p.status_pendaftaran)) {
            p.status_pendaftaran = "DITOLAK";

            // 🔥 UPDATE KUOTA TERISI (KURANGI 1)
            // Pastikan rute tidak null
            if (p.rute != null) {
                Rute r = Rute.findById(p.rute.rute_id, LockModeType.PESSIMISTIC_WRITE);
                if (r.kuota_terisi != null && r.kuota_terisi > 0) {
                    r.kuota_terisi = r.kuota_terisi - 1;
                }
            }
            p.persist();
        }
    }

    // =================================================================
    // 5. ADMIN: UPDATE STATUS KELUARGA (FIXED POIN 3)
    // =================================================================
    @Transactional
    public String updateStatusKeluarga(Long userId, String statusBaru, String alasan) throws Exception {
        List<PendaftaranMudik> keluarga = PendaftaranMudik.list("user.user_id = ?1", userId);
        if (keluarga.isEmpty()) throw new Exception("Data keluarga tidak ditemukan!");

        // Ambil Rute untuk lock (Cek null safety jaga-jaga)
        if (keluarga.get(0).rute == null) throw new Exception("Data rute tidak valid!");
        Rute ruteLocked = Rute.findById(keluarga.get(0).rute.rute_id, LockModeType.PESSIMISTIC_WRITE);

        for (PendaftaranMudik p : keluarga) {
            String statusLama = p.status_pendaftaran;

            // 🔥 FIX POIN 3: PROTEKSI DATA DITOLAK
            // Jika status lama sudah FINAL (Ditolak/Batal), JANGAN diubah jadi DITERIMA.
            // Pengecualian:
            // 1. Jika Admin mau 'DITOLAK' lagi (Update alasan) -> Boleh
            // 2. Jika Admin mau 'MENUNGGU VERIFIKASI' (Reset/Batal Tolak) -> Boleh (Masuk Case B)
            if (("DITOLAK".equals(statusLama) || "DIBATALKAN".equals(statusLama))
                    && !"DITOLAK".equalsIgnoreCase(statusBaru)
                    && !"MENUNGGU VERIFIKASI".equalsIgnoreCase(statusBaru)) {
                continue; // Skip anggota ini, biarkan tetap Ditolak/Batal
            }

            // A. Kalau DITOLAK -> Kuota Terisi Berkurang
            if ("DITOLAK".equalsIgnoreCase(statusBaru)) {
                if (alasan == null || alasan.isBlank()) throw new Exception("Alasan tolak wajib diisi!");

                // Cek status lama, kalau statusnya memakan kuota, baru dikurangi
                if ("MENUNGGU VERIFIKASI".equals(statusLama) || "DITERIMA H-3".equals(statusLama) || "DITERIMA".equals(statusLama)) {
                    if (ruteLocked.kuota_terisi != null && ruteLocked.kuota_terisi > 0) {
                        ruteLocked.kuota_terisi -= 1;
                    }
                }
                p.alasan_tolak = alasan;
            }
            // B. Kalau RESET (Ditolak -> Menunggu) -> Kuota Terisi Bertambah
            else if (("DITOLAK".equals(statusLama) || "DIBATALKAN".equals(statusLama)) && "MENUNGGU VERIFIKASI".equals(statusBaru)) {
                if (ruteLocked.getSisaKuota() <= 0) throw new Exception("Kuota sudah penuh!");

                if (ruteLocked.kuota_terisi == null) ruteLocked.kuota_terisi = 0;
                ruteLocked.kuota_terisi += 1;

                p.alasan_tolak = null;
            }

            p.status_pendaftaran = statusBaru;
            p.persist();
        }

        // Return Link WA (Ambil dari data pertama yang ketemu)
        String tipeWa = statusBaru.equals("DITOLAK") ? "TOLAK_DATA" : "TERIMA";
        return whatsAppService.generateLink(keluarga.get(0).no_hp_peserta, tipeWa, keluarga.get(0), alasan);
    }

    // =================================================================
    // 6. ADMIN: TOLAK KELUARGA (Batch)
    // =================================================================
    @Transactional
    public String tolakPendaftaranKeluarga(Long userId, String alasan) throws Exception {
        List<PendaftaranMudik> keluarga = PendaftaranMudik.list("user.user_id = ?1 AND status_pendaftaran NOT IN ('DITOLAK', 'DIBATALKAN')", userId);
        if (keluarga.isEmpty()) throw new Exception("Tidak ada pendaftaran aktif untuk ditolak.");

        Rute ruteLocked = Rute.findById(keluarga.get(0).rute.rute_id, LockModeType.PESSIMISTIC_WRITE);

        for (PendaftaranMudik p : keluarga) {
            p.status_pendaftaran = "DITOLAK";
            p.alasan_tolak = alasan;

            // Bayi tidak makan kursi, jadi jangan kurangi kuota
            if (!"BAYI".equalsIgnoreCase(p.kategori_penumpang)) {
                if (ruteLocked.kuota_terisi != null && ruteLocked.kuota_terisi > 0) {
                    ruteLocked.kuota_terisi -= 1;
                }
            }
            p.persist();
        }

        return whatsAppService.generateLink(keluarga.get(0).no_hp_peserta, "TOLAK_DATA", keluarga.get(0), alasan);
    }

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