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

        List<PendaftaranMudik> listToSave = new ArrayList<>();

        for (int i = 0; i < jumlahPeserta; i++) {
            PendaftaranMudik p = new PendaftaranMudik();
            p.user = user;
            p.nama_peserta = form.nama_peserta.get(i).toUpperCase();
            p.uuid = UUID.randomUUID().toString();

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

            String hp = (form.no_hp_peserta != null && i < form.no_hp_peserta.size()) ? form.no_hp_peserta.get(i) : user.no_hp;
            p.no_hp_peserta = hp;

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

        // LOCK DB
        Rute ruteLocked = Rute.findById(rute.rute_id, LockModeType.PESSIMISTIC_WRITE);
        if (ruteLocked.getSisaKuota() < jumlahPeserta) {
            throw new Exception("Mohon maaf, Kuota Rute baru saja habis!");
        }

        for (PendaftaranMudik p : listToSave) {
            p.rute = ruteLocked;
            p.persist();
        }

        if (ruteLocked.kuota_terisi == null) ruteLocked.kuota_terisi = 0;
        ruteLocked.kuota_terisi += jumlahPeserta;

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
        Rute ruteLocked = null;
        if (!keluarga.isEmpty()) {
            ruteLocked = Rute.findById(keluarga.get(0).rute.rute_id, LockModeType.PESSIMISTIC_WRITE);
        }

        for (PendaftaranMudik p : keluarga) {
            if (idsTetapIkut.contains(p.pendaftaran_id)) {
                p.status_pendaftaran = "TERVERIFIKASI/ SIAP BERANGKAT";
                countHadir++;
            } else {
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

        Rute r = Rute.findById(p.rute.rute_id, LockModeType.PESSIMISTIC_WRITE);
        if (r.getSisaKuota() <= 0) throw new Exception("Kuota Rute Penuh! Tidak bisa mengajukan ulang.");

        p.status_pendaftaran = "MENUNGGU VERIFIKASI";
        p.alasan_tolak = null;

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
    // 5. ADMIN: UPDATE STATUS KELUARGA (MANUAL)
    // =================================================================
    @Transactional
    public String updateStatusKeluarga(Long userId, String statusBaru, String alasan) throws Exception {
        List<PendaftaranMudik> keluarga = PendaftaranMudik.list("user.user_id = ?1", userId);
        if (keluarga.isEmpty()) throw new Exception("Data keluarga tidak ditemukan!");

        Rute ruteLocked = Rute.findById(keluarga.get(0).rute.rute_id, LockModeType.PESSIMISTIC_WRITE);

        for (PendaftaranMudik p : keluarga) {
            String statusLama = p.status_pendaftaran;

            if (("DITOLAK".equals(statusLama) || "DIBATALKAN".equals(statusLama))
                    && !"DITOLAK".equalsIgnoreCase(statusBaru)
                    && !"MENUNGGU VERIFIKASI".equalsIgnoreCase(statusBaru)) {
                continue;
            }

            if ("DITOLAK".equalsIgnoreCase(statusBaru)) {
                if (alasan == null || alasan.isBlank()) throw new Exception("Alasan tolak wajib diisi!");
                if (!"DITOLAK".equals(statusLama) && !"DIBATALKAN".equals(statusLama)) {
                    if (ruteLocked.kuota_terisi > 0) ruteLocked.kuota_terisi -= 1;
                }
                p.alasan_tolak = alasan;
            }
            else if (("DITOLAK".equals(statusLama) || "DIBATALKAN".equals(statusLama)) && "MENUNGGU VERIFIKASI".equals(statusBaru)) {
                if (ruteLocked.getSisaKuota() <= 0) throw new Exception("Kuota sudah penuh!");
                if (ruteLocked.kuota_terisi == null) ruteLocked.kuota_terisi = 0;
                ruteLocked.kuota_terisi += 1;
                p.alasan_tolak = null;
            }

            p.status_pendaftaran = statusBaru;
            p.persist();
        }

        String tipeWa = "TERIMA";
        if ("DITOLAK".equals(statusBaru)) {
            tipeWa = "TOLAK_DATA";
        } else if ("DITERIMA H-3".equals(statusBaru)) {
            tipeWa = "DITERIMA(H-3)";
        }

        // 🔥 FIX PENTING: AMBIL HP YANG VALID
        String hpValid = getValidPhoneNumber(keluarga);
        return whatsAppService.generateLink(hpValid, tipeWa, keluarga.get(0), alasan);
    }

    // =================================================================
    // 6. ADMIN: TOLAK KELUARGA (BATCH)
    // =================================================================
    @Transactional
    public String tolakPendaftaranKeluarga(Long userId, String alasan) throws Exception {
        return updateStatusKeluarga(userId, "DITOLAK", alasan);
    }

    // =================================================================
    // 7. ADMIN: VERIFIKASI CUSTOM (CHECKBOX) - DENGAN WA PINTAR 🔥
    // =================================================================
    @Transactional
    public String verifikasiCustom(Long userId, List<Long> idsDitolak, String alasan) throws Exception {
        List<PendaftaranMudik> keluarga = PendaftaranMudik.list("user.user_id = ?1", userId);
        if (keluarga.isEmpty()) throw new Exception("Data tidak ditemukan.");

        Rute ruteLocked = Rute.findById(keluarga.get(0).rute.rute_id, LockModeType.PESSIMISTIC_WRITE);

        int countDitolak = 0;

        for (PendaftaranMudik p : keluarga) {
            String statusLama = p.status_pendaftaran;
            boolean isRejected = idsDitolak.contains(p.pendaftaran_id);

            // A. REJECT
            if (isRejected) {
                if (!"DITOLAK".equals(statusLama) && !"DIBATALKAN".equals(statusLama)) {
                    if (!"BAYI".equalsIgnoreCase(p.kategori_penumpang)) {
                        if (ruteLocked.kuota_terisi > 0) ruteLocked.kuota_terisi -= 1;
                    }
                }
                p.status_pendaftaran = "DITOLAK";
                p.alasan_tolak = alasan;
                countDitolak++;
            }
            // B. ACCEPT (AUTO DITERIMA H-3 UNTUK KONFIRMASI)
            else {
                if ("DITOLAK".equals(statusLama) || "DIBATALKAN".equals(statusLama)) {
                    if (ruteLocked.getSisaKuota() <= 0) throw new Exception("Gagal ACC. Kuota Penuh!");
                    ruteLocked.kuota_terisi += 1;
                }
                p.status_pendaftaran = "DITERIMA H-3";
                p.alasan_tolak = null;
            }
            p.persist();
        }

        // 🔥 LOGIKA WA
        String tipeWa;
        String pesanAlasan = null;

        if (countDitolak > 0) {
            tipeWa = "TOLAK_DATA";
            pesanAlasan = "Terdapat " + countDitolak + " data penumpang yang perlu diperbaiki (" + alasan + ").";
        } else {
            tipeWa = "DITERIMA(H-3)";
        }

        // 🔥 FIX PENTING: AMBIL HP YANG VALID (JANGAN CUMA GET(0))
        String hpValid = getValidPhoneNumber(keluarga);
        return whatsAppService.generateLink(hpValid, tipeWa, keluarga.get(0), pesanAlasan);
    }

    // 🔥 HELPER: CARI HP VALID DI KELUARGA / USER
    private String getValidPhoneNumber(List<PendaftaranMudik> keluarga) {
        if (keluarga == null || keluarga.isEmpty()) return null;

        // 1. Coba cari di salah satu penumpang yang HP-nya valid
        for (PendaftaranMudik p : keluarga) {
            if (p.no_hp_peserta != null && p.no_hp_peserta.length() > 7) {
                return p.no_hp_peserta;
            }
        }

        // 2. Kalau semua null, ambil dari Akun User (Kepala Keluarga)
        if (keluarga.get(0).user != null && keluarga.get(0).user.no_hp != null) {
            return keluarga.get(0).user.no_hp;
        }

        return null; // Nyerah, balikin null (nanti WA Service return #)
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