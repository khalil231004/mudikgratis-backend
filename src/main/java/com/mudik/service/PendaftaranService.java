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

        // VALIDASI KUOTA RUTE (Locking)
        Rute ruteLocked = Rute.findById(rute.rute_id, LockModeType.PESSIMISTIC_WRITE);
        if (ruteLocked.getSisaKuota() < jumlahPeserta) {
            throw new Exception("Kuota Rute Habis! Sisa tiket: " + ruteLocked.getSisaKuota());
        }

        // VALIDASI LIMIT AKUN (Max 6)
        long sudahDaftar = PendaftaranMudik.count("user.user_id = ?1 AND status_pendaftaran NOT IN ('DITOLAK', 'DIBATALKAN')", user.user_id);
        if (sudahDaftar + jumlahPeserta > 6) {
            throw new Exception("Kuota akun penuh! Sisa slot anda: " + (6 - sudahDaftar));
        }

        // VALIDASI NIK DUPLIKAT
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

        // LOOP SIMPAN PESERTA
        for (int i = 0; i < jumlahPeserta; i++) {
            PendaftaranMudik p = new PendaftaranMudik();
            p.user = user;
            p.rute = ruteLocked; // Pakai locked rute
            p.nama_peserta = form.nama_peserta.get(i).toUpperCase();

            String rawNik = (form.nik_peserta != null && i < form.nik_peserta.size()) ? form.nik_peserta.get(i).trim() : "-";
            if (rawNik.length() > 16) rawNik = rawNik.substring(0, 16);
            p.nik_peserta = rawNik;

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
            p.no_hp_peserta = (form.no_hp_peserta != null && i < form.no_hp_peserta.size()) ? form.no_hp_peserta.get(i) : user.no_hp;

            if (form.fotoBukti != null && i < form.fotoBukti.size()) {
                FileUpload file = form.fotoBukti.get(i);
                if (file != null && file.fileName() != null && !file.fileName().isEmpty()) {
                    p.foto_identitas_path = uploadFileHelper(file, p.nik_peserta);
                }
            }

            p.status_pendaftaran = "MENUNGGU VERIFIKASI";
            p.kode_booking = "MDK-" + System.currentTimeMillis() + "-" + (i + 1);
            p.persist();
        }

        // Update kuota
        if (ruteLocked.kuota_terisi == null) ruteLocked.kuota_terisi = 0;
        ruteLocked.kuota_terisi += jumlahPeserta;
    }

    // =================================================================
    // 2. PROSES KONFIRMASI KEHADIRAN (BATCH)
    // =================================================================
    @Transactional
    public String prosesKonfirmasi(Long userId, List<Long> idsTetapIkut) throws Exception {
        List<PendaftaranMudik> keluarga = PendaftaranMudik.list("user.user_id = ?1 AND status_pendaftaran = 'DITERIMA H-3'", userId);
        if (keluarga.isEmpty()) throw new Exception("Tidak ada data DITERIMA H-3.");

        int countHadir = 0; int countBatal = 0;
        for (PendaftaranMudik p : keluarga) {
            if (idsTetapIkut.contains(p.pendaftaran_id)) {
                p.status_pendaftaran = "TERVERIFIKASI/ SIAP BERANGKAT";
                countHadir++;
            } else {
                p.status_pendaftaran = "DIBATALKAN";
                Rute r = Rute.findById(p.rute.rute_id, LockModeType.PESSIMISTIC_WRITE);
                if (r.kuota_terisi > 0) r.kuota_terisi -= 1;
                countBatal++;
            }
            p.persist();
        }
        return countHadir + " Siap Berangkat, " + countBatal + " Dibatalkan.";
    }

    // =================================================================
    // 3. FITUR EDIT / AJUKAN ULANG
    // =================================================================
    @Transactional
    public void editPendaftaran(Long userId, Long pendaftaranId, PendaftaranMultipartForm form) throws Exception {
        PendaftaranMudik p = PendaftaranMudik.findById(pendaftaranId);
        if (p == null || !p.user.user_id.equals(userId)) throw new Exception("Data tidak valid.");

        if (!"DITOLAK".equals(p.status_pendaftaran)) throw new Exception("Hanya status DITOLAK yang bisa diperbaiki.");

        Rute r = Rute.findById(p.rute.rute_id, LockModeType.PESSIMISTIC_WRITE);
        if (r.getSisaKuota() <= 0) throw new Exception("Kuota Rute Penuh!");

        if (form.nama_peserta != null) p.nama_peserta = form.nama_peserta.get(0).toUpperCase();
        if (form.nik_peserta != null) p.nik_peserta = form.nik_peserta.get(0);

        p.status_pendaftaran = "MENUNGGU VERIFIKASI";
        p.alasan_tolak = null;
        r.kuota_terisi = (r.kuota_terisi == null ? 0 : r.kuota_terisi) + 1;
        p.persist();
    }

    // =================================================================
    // 4. ADMIN: TOLAK PESERTA SATUAN (HELPER)
    // =================================================================
    @Transactional
    public void adminTolakPeserta(Long pendaftaranId) {
        PendaftaranMudik p = PendaftaranMudik.findById(pendaftaranId);
        if (p == null) return;

        if (!"DITOLAK".equals(p.status_pendaftaran) && !"DIBATALKAN".equals(p.status_pendaftaran)) {
            p.status_pendaftaran = "DITOLAK";
            if (p.rute != null && p.rute.kuota_terisi > 0) {
                p.rute.kuota_terisi -= 1;
            }
            p.persist();
        }
    }

    // =================================================================
    // 5. ADMIN: UPDATE STATUS KELUARGA (Fix Return String & Logic)
    // =================================================================
    @Transactional
    public String updateStatusKeluarga(Long userId, String statusBaru, String alasan) throws Exception {
        List<PendaftaranMudik> keluarga = PendaftaranMudik.list("user.user_id = ?1", userId);
        if (keluarga.isEmpty()) throw new Exception("Data keluarga tidak ditemukan!");

        Rute rute = Rute.findById(keluarga.get(0).rute.rute_id, LockModeType.PESSIMISTIC_WRITE);

        for (PendaftaranMudik p : keluarga) {
            String statusLama = p.status_pendaftaran;

            if ("DITOLAK".equalsIgnoreCase(statusBaru)) {
                if (alasan == null || alasan.isBlank()) throw new Exception("Alasan tolak wajib diisi!");
                if ("MENUNGGU VERIFIKASI".equals(statusLama) || "DITERIMA H-3".equals(statusLama)) {
                    rute.kuota_terisi = Math.max(0, (rute.kuota_terisi != null ? rute.kuota_terisi : 0) - 1);
                }
                p.alasan_tolak = alasan;
            }
            else if ("DITOLAK".equals(statusLama) && "MENUNGGU VERIFIKASI".equals(statusBaru)) {
                if (rute.getSisaKuota() <= 0) throw new Exception("Kuota sudah habis!");
                rute.kuota_terisi = (rute.kuota_terisi == null ? 0 : rute.kuota_terisi) + 1;
                p.alasan_tolak = null;
            }

            p.status_pendaftaran = statusBaru;
            p.persist();
        }

        // 🔥 RETURN LINK WA (Bukan void)
        String tipeWa = statusBaru.equals("DITOLAK") ? "TOLAK_DATA" : "TERIMA";
        return whatsAppService.generateLink(keluarga.get(0).no_hp_peserta, tipeWa, keluarga.get(0), alasan);
    }

    // =================================================================
    // 6. ADMIN: TOLAK KELUARGA (Missing Method Fixed)
    // =================================================================
    @Transactional
    public String tolakPendaftaranKeluarga(Long userId, String alasan) throws Exception {
        List<PendaftaranMudik> keluarga = PendaftaranMudik.list("user.user_id = ?1 AND status_pendaftaran NOT IN ('DITOLAK', 'DIBATALKAN')", userId);
        if (keluarga.isEmpty()) throw new Exception("Tidak ada pendaftaran aktif untuk ditolak.");

        Rute rute = Rute.findById(keluarga.get(0).rute.rute_id, LockModeType.PESSIMISTIC_WRITE);

        for (PendaftaranMudik p : keluarga) {
            p.status_pendaftaran = "DITOLAK";
            p.alasan_tolak = alasan;
            // Kembalikan kuota (kecuali Bayi)
            if (!"BAYI".equalsIgnoreCase(p.kategori_penumpang)) {
                rute.kuota_terisi = Math.max(0, (rute.kuota_terisi != null ? rute.kuota_terisi : 0) - 1);
            }
            p.persist();
        }

        // 🔥 RETURN LINK WA
        return whatsAppService.generateLink(keluarga.get(0).no_hp_peserta, "TOLAK_DATA", keluarga.get(0), alasan);
    }

    // HELPER UPLOAD
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