package com.mudik.service;

import com.mudik.model.PendaftaranMudik;
import com.mudik.model.PortalConfig;
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
import java.util.*;

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

        // ── GLOBAL PORTAL CHECK ────────────────────────────────
        PortalConfig portalCfg = PortalConfig.getInstance();
        if (!Boolean.TRUE.equals(portalCfg.sesi_aktif)) {
            throw new Exception(portalCfg.pesan_sesi_berakhir != null
                    ? portalCfg.pesan_sesi_berakhir
                    : "Program Mudik Gratis telah berakhir.");
        }
        if (!Boolean.TRUE.equals(portalCfg.portal_mudik_open)) {
            throw new Exception(portalCfg.pesan_mudik_tutup != null
                    ? portalCfg.pesan_mudik_tutup
                    : "Pendaftaran Mudik Gratis saat ini ditutup.");
        }
        // ── END GLOBAL PORTAL CHECK ────────────────────────────

        // FIX 11: Cek portal pendaftaran per-rute
        if (rute.is_portal_open != null && !rute.is_portal_open) {
            throw new Exception("Maaf, portal pendaftaran untuk rute ini saat ini DITUTUP. Silakan hubungi Dishub Aceh.");
        }

        // Cek Limit Akun
        long sudahDaftar = PendaftaranMudik.count("user.user_id = ?1 AND status_pendaftaran NOT IN ('DITOLAK', 'DIBATALKAN')", user.user_id);
        if (sudahDaftar + jumlahPeserta > 6) {
            throw new Exception("Kuota akun penuh! Sisa slot anda: " + (6 - sudahDaftar));
        }

        // 🔥 3. VALIDASI PENTING: SATU USER = SATU RUTE (LOCK RUTE)
        // Cari apakah user sudah punya pendaftaran aktif sebelumnya?
        PendaftaranMudik existingBooking = PendaftaranMudik.find("user.user_id = ?1 AND status_pendaftaran NOT IN ('DITOLAK', 'DIBATALKAN')", user.user_id).firstResult();

        if (existingBooking != null) {
            // Jika sudah ada, rute yang dipilih SKRANG wajib SAMA dengan rute SEBELUMNYA
            if (!existingBooking.rute.rute_id.equals(rute.rute_id)) {
                throw new Exception("Mohon Maaf, Satu Akun hanya boleh memilih 1 Rute Tujuan.\nAnda sudah terdaftar di rute: " + existingBooking.rute.tujuan);
            }
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
                    // FIX 1: Anak = umur < 17, Bayi = umur < 2
                    if (umur < 2) {
                        p.kategori_penumpang = "BAYI";
                    } else if (umur < 17) {
                        p.kategori_penumpang = "ANAK";
                    } else {
                        p.kategori_penumpang = "DEWASA";
                    }
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

            // Simpan HP jika diisi, jika tidak biarkan ambil dari HP User nanti saat verifikasi
            String hp = (form.no_hp_peserta != null && i < form.no_hp_peserta.size()) ? form.no_hp_peserta.get(i) : null;
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

        // FIX 1: Update tanggal_lahir dan recalculate kategori penumpang saat edit
        if (form.tanggal_lahir != null && !form.tanggal_lahir.isEmpty() && !form.tanggal_lahir.get(0).isBlank()) {
            try {
                LocalDate tgl = LocalDate.parse(form.tanggal_lahir.get(0));
                p.tanggal_lahir = tgl;
                int umur = Period.between(tgl, LocalDate.now()).getYears();
                if (umur < 2) {
                    p.kategori_penumpang = "BAYI";
                } else if (umur < 5) {
                    p.kategori_penumpang = "ANAK";
                } else {
                    p.kategori_penumpang = "DEWASA";
                }
            } catch (Exception ignored) {}
        }

        if (form.fotoBukti != null && !form.fotoBukti.isEmpty()) {
            FileUpload file = form.fotoBukti.get(0);
            if (file != null && file.fileName() != null && !file.fileName().isEmpty() && file.size() > 0) {
                p.foto_identitas_path = uploadFileHelper(file, p.nik_peserta);
            }
        }

        Rute r = Rute.findById(p.rute.rute_id, LockModeType.PESSIMISTIC_WRITE);
        if (r.getSisaKuota() <= 0) throw new Exception("Kuota Rute Penuh! Tidak bisa mengajukan ulang.");

        // ── Set status ke MENUNGGU VERIFIKASI + kembalikan kuota ──
        // User sudah perbaiki data → tunggu admin re-verifikasi, BUKAN auto DITERIMA H-3
        p.status_pendaftaran = "MENUNGGU VERIFIKASI";
        p.alasan_tolak = null;
        if (r.kuota_terisi == null) r.kuota_terisi = 0;
        r.kuota_terisi += 1;
        r.persist();
        p.persist();

        // ── CEK SISA KELUARGA: jika semua DITOLAK sudah diperbaiki,
        //    kembalikan PENDING → MENUNGGU VERIFIKASI agar admin bisa verif ulang sekaligus ──
        long masihDitolak = PendaftaranMudik.count(
                "user.user_id = ?1 AND pendaftaran_id != ?2 AND status_pendaftaran = 'DITOLAK'",
                userId, pendaftaranId
        );

        if (masihDitolak == 0) {
            // Tidak ada yang DITOLAK lagi → PENDING kembali ke MENUNGGU VERIFIKASI
            // Admin harus approve ulang untuk memberikan status DITERIMA H-3
            List<PendaftaranMudik> keluarga = PendaftaranMudik.list("user.user_id = ?1", userId);
            for (PendaftaranMudik anggota : keluarga) {
                if ("PENDING".equals(anggota.status_pendaftaran)) {
                    anggota.status_pendaftaran = "MENUNGGU VERIFIKASI";
                    anggota.persist();
                }
            }
        }
        // Jika masih ada yang DITOLAK → yang lain tetap PENDING, yang ini MENUNGGU VERIFIKASI
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
    // 5. ADMIN: UPDATE STATUS KELUARGA (MANUAL) — DENGAN STATUS PENDING
    // =================================================================
    @Transactional
    public String updateStatusKeluarga(Long userId, String statusBaru, String alasan) throws Exception {
        List<PendaftaranMudik> keluarga = PendaftaranMudik.list("user.user_id = ?1", userId);
        if (keluarga.isEmpty()) throw new Exception("Data keluarga tidak ditemukan!");

        Rute ruteLocked = Rute.findById(keluarga.get(0).rute.rute_id, LockModeType.PESSIMISTIC_WRITE);

        for (PendaftaranMudik p : keluarga) {
            String statusLama = p.status_pendaftaran;

            // Skip yang sudah final kecuali saat RESET
            boolean sudahFinal = "DITOLAK".equals(statusLama) || "DIBATALKAN".equals(statusLama);
            if (sudahFinal && !"MENUNGGU VERIFIKASI".equalsIgnoreCase(statusBaru)) continue;

            if ("DITOLAK".equalsIgnoreCase(statusBaru)) {
                // Kurangi kuota jika sebelumnya aktif
                boolean wasActive = !"DITOLAK".equals(statusLama) && !"DIBATALKAN".equals(statusLama) && !"PENDING".equals(statusLama);
                if (wasActive) {
                    if (ruteLocked.kuota_terisi != null && ruteLocked.kuota_terisi > 0)
                        ruteLocked.kuota_terisi -= 1;
                }
                p.alasan_tolak = (alasan != null && !alasan.isBlank()) ? alasan : "Ditolak oleh admin";
            } else if ("MENUNGGU VERIFIKASI".equals(statusBaru)) {
                // RESET: pulihkan kuota dari DITOLAK/DIBATALKAN/PENDING
                if (sudahFinal || "PENDING".equals(statusLama)) {
                    if (ruteLocked.getSisaKuota() <= 0) throw new Exception("Kuota sudah penuh!");
                    if (ruteLocked.kuota_terisi == null) ruteLocked.kuota_terisi = 0;
                    if (sudahFinal) ruteLocked.kuota_terisi += 1; // PENDING tidak kurangi kuota, jadi tidak perlu tambah
                }
                p.alasan_tolak = null;
            }

            p.status_pendaftaran = statusBaru;
            p.persist();
        }

        ruteLocked.persist();

        String tipeWa = "TERIMA";
        if ("DITOLAK".equalsIgnoreCase(statusBaru)) tipeWa = "TOLAK_DATA";
        else if ("DITERIMA H-3".equals(statusBaru)) tipeWa = "DITERIMA(H-3)";

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
    // 7. ADMIN: VERIFIKASI CUSTOM (CHECKBOX) - STATUS PENDING EKSPLISIT
    // =================================================================
    @Transactional
    public String verifikasiCustom(Long userId, List<Long> idsDitolak, String alasan) throws Exception {
        List<PendaftaranMudik> keluarga = PendaftaranMudik.list("user.user_id = ?1", userId);
        if (keluarga.isEmpty()) throw new Exception("Data tidak ditemukan.");

        Rute ruteLocked = Rute.findById(keluarga.get(0).rute.rute_id, LockModeType.PESSIMISTIC_WRITE);

        int countDitolak = 0;

        // ── PASS 1: proses yang dicentang (DITOLAK) ──
        for (PendaftaranMudik p : keluarga) {
            String statusLama = p.status_pendaftaran;
            boolean isRejected = idsDitolak.contains(p.pendaftaran_id);

            if (isRejected) {
                // Kurangi kuota hanya jika sebelumnya status aktif (bukan sudah ditolak/batal/ditunda)
                boolean wasActive = !"DITOLAK".equals(statusLama)
                        && !"DIBATALKAN".equals(statusLama)
                        && !"PENDING".equals(statusLama);
                if (wasActive && !"BAYI".equalsIgnoreCase(p.kategori_penumpang)) {
                    if (ruteLocked.kuota_terisi != null && ruteLocked.kuota_terisi > 0)
                        ruteLocked.kuota_terisi -= 1;
                }
                p.status_pendaftaran = "DITOLAK";
                p.alasan_tolak = alasan;
                p.persist();
                countDitolak++;
            } else if ("DITOLAK".equals(statusLama) || "DIBATALKAN".equals(statusLama)) {
                // Sebelumnya ditolak/batal tapi sekarang tidak dicentang (di-uncheck) → kembalikan kuota
                if (ruteLocked.getSisaKuota() <= 0) throw new Exception("Gagal ACC. Kuota Penuh!");
                if (ruteLocked.kuota_terisi == null) ruteLocked.kuota_terisi = 0;
                ruteLocked.kuota_terisi += 1;
                p.status_pendaftaran = "MENUNGGU VERIFIKASI";
                p.alasan_tolak = null;
                p.persist();
            }
        }

        ruteLocked.persist();

        // ── PASS 2: tentukan status anggota yang tidak dicentang ──
        if (countDitolak > 0) {
            // Ada yang ditolak → anggota lain yang masih aktif → status PENDING
            for (PendaftaranMudik p : keluarga) {
                boolean isRejected = idsDitolak.contains(p.pendaftaran_id);
                String status = p.status_pendaftaran;
                boolean sudahFinal = "DITOLAK".equals(status) || "DIBATALKAN".equals(status);
                if (!isRejected && !sudahFinal) {
                    p.status_pendaftaran = "PENDING";
                    p.alasan_tolak = null;
                    p.persist();
                }
            }
        } else {
            // Tidak ada yang ditolak → SEMUA valid → set DITERIMA H-3
            // Juga pulihkan anggota yang sebelumnya PENDING
            for (PendaftaranMudik p : keluarga) {
                String status = p.status_pendaftaran;
                if (!"DITOLAK".equals(status) && !"DIBATALKAN".equals(status)) {
                    p.status_pendaftaran = "DITERIMA H-3";
                    p.alasan_tolak = null;
                    p.persist();
                }
            }
        }

        // WA
        String tipeWa;
        String pesanAlasan = null;
        if (countDitolak > 0) {
            tipeWa = "TOLAK_DATA";
            pesanAlasan = "Terdapat " + countDitolak + " data penumpang yang perlu diperbaiki (" + alasan + ").";
        } else {
            tipeWa = "DITERIMA(H-3)";
        }

        String hpValid = getValidPhoneNumber(keluarga);
        return whatsAppService.generateLink(hpValid, tipeWa, keluarga.get(0), pesanAlasan);
    }


    // =================================================================
    // 8. ADMIN: GET PENDAFTAR PAGINATED (DENGAN FILTER & LIMIT)
    // =================================================================
    public Map<String, Object> getPendaftarAdminPaginated(int page, int limit, String search, String rute, String status) {

        // ── Build WHERE clause (pakai native JPQL alias 'p') ──────
        StringBuilder where = new StringBuilder("p.user IS NOT NULL");
        Map<String, Object> params = new HashMap<>();

        if (search != null && !search.trim().isEmpty()) {
            where.append(" AND (LOWER(p.nama_peserta) LIKE :search"
                    + " OR p.nik_peserta LIKE :search"
                    + " OR LOWER(p.user.nama_lengkap) LIKE :search)");
            params.put("search", "%" + search.trim().toLowerCase() + "%");
        }
        if (rute != null && !rute.trim().isEmpty()) {
            where.append(" AND p.rute.tujuan = :rute");
            params.put("rute", rute.trim());
        }
        if (status != null && !status.trim().isEmpty()) {
            if ("SIAP BERANGKAT".equals(status.trim())) {
                where.append(" AND (p.status_pendaftaran LIKE '%SIAP BERANGKAT%'"
                        + " OR p.status_pendaftaran LIKE '%TERKONFIRMASI%')");
            } else {
                where.append(" AND p.status_pendaftaran = :status");
                params.put("status", status.trim());
            }
        }

        // ── 1. Hitung total keluarga (DISTINCT user_id) ───────────
        StringBuilder countJpql = new StringBuilder(
                "SELECT COUNT(DISTINCT p.user.user_id) FROM PendaftaranMudik p WHERE ");
        countJpql.append(where);

        var countQ = PendaftaranMudik.getEntityManager()
                .createQuery(countJpql.toString(), Long.class);
        params.forEach(countQ::setParameter);
        long totalKeluarga = countQ.getSingleResult();
        int totalPages = totalKeluarga == 0 ? 1 : (int) Math.ceil((double) totalKeluarga / limit);

        // ── 2. Ambil user_id halaman ini ──────────────────────────
        StringBuilder userJpql = new StringBuilder(
                "SELECT DISTINCT p.user.user_id FROM PendaftaranMudik p WHERE ");
        userJpql.append(where).append(" ORDER BY p.user.user_id DESC");

        var userQ = PendaftaranMudik.getEntityManager()
                .createQuery(userJpql.toString(), Long.class);
        params.forEach(userQ::setParameter);
        int offset = (page - 1) * limit;
        List<Long> userIds = userQ.setFirstResult(offset).setMaxResults(limit).getResultList();

        // ── 3. Ambil semua pendaftaran untuk user_id tersebut ─────
        List<PendaftaranMudik> rows = new ArrayList<>();
        if (!userIds.isEmpty()) {
            var dataQ = PendaftaranMudik.getEntityManager().createQuery(
                    "SELECT p FROM PendaftaranMudik p"
                            + " LEFT JOIN FETCH p.user"
                            + " LEFT JOIN FETCH p.rute"
                            + " LEFT JOIN FETCH p.kendaraan"
                            + " WHERE p.user.user_id IN :ids"
                            + " ORDER BY p.user.user_id DESC, p.created_at DESC",
                    PendaftaranMudik.class);
            dataQ.setParameter("ids", userIds);
            rows = dataQ.getResultList();
        }

        // ── 4. Map ke DTO ─────────────────────────────────────────
        List<Map<String, Object>> mapped = new ArrayList<>();
        for (PendaftaranMudik p : rows) {
            Map<String, Object> m = new HashMap<>();
            m.put("id",               p.pendaftaran_id);
            m.put("uuid",             p.uuid != null ? p.uuid : "");
            m.put("nama_peserta",     p.nama_peserta);
            m.put("nik_peserta",      p.nik_peserta);
            m.put("jenis_kelamin",    p.jenis_kelamin != null ? p.jenis_kelamin : "");
            m.put("kategori",         p.kategori_penumpang != null ? p.kategori_penumpang : "");
            m.put("status",           p.status_pendaftaran != null ? p.status_pendaftaran : "UNKNOWN");
            m.put("kode_booking",     p.kode_booking != null ? p.kode_booking : "-");
            m.put("alasan_tolak",     p.alasan_tolak != null ? p.alasan_tolak : "-");
            m.put("id_keluarga",      p.user != null ? p.user.user_id : 0);
            m.put("nama_kepala_keluarga", p.user != null ? p.user.nama_lengkap : "Tanpa Akun");
            m.put("rute_tujuan",      p.rute != null ? p.rute.tujuan : "Unknown");
            m.put("tgl_berangkat",    p.rute != null ? p.rute.getFormattedDate() : "-");
            m.put("nama_bus",         p.kendaraan != null ? p.kendaraan.nama_armada : "Belum Plotting");

            String hp = (p.no_hp_peserta != null && p.no_hp_peserta.length() > 5)
                    ? p.no_hp_peserta
                    : (p.user != null && p.user.no_hp != null ? p.user.no_hp : "");
            m.put("no_hp_target", hp);

            if (p.foto_identitas_path != null && !p.foto_identitas_path.isBlank()) {
                m.put("foto_bukti", "/uploads/" + new java.io.File(p.foto_identitas_path).getName());
            } else {
                m.put("foto_bukti", null);
            }
            mapped.add(m);
        }

        Map<String, Object> response = new HashMap<>();
        response.put("data",          mapped);
        response.put("totalKeluarga", totalKeluarga);
        response.put("totalPages",    totalPages);
        response.put("currentPage",   page);
        return response;
    }

    // 🔥 HELPER: CARI HP VALID    // 🔥 HELPER: CARI HP VALID (PENUMPANG -> USER)
    private String getValidPhoneNumber(List<PendaftaranMudik> keluarga) {
        if (keluarga == null || keluarga.isEmpty()) return null;

        // 1. Cek nomor HP di data salah satu penumpang dalam rombongan
        for (PendaftaranMudik p : keluarga) {
            if (p.no_hp_peserta != null && p.no_hp_peserta.trim().length() > 7) {
                return p.no_hp_peserta.trim();
            }
        }

        // 2. Kalau semua penumpang kosong HP-nya, ambil dari nomor HP akun (User)
        if (keluarga.get(0).user != null && keluarga.get(0).user.no_hp != null) {
            return keluarga.get(0).user.no_hp.trim();
        }

        return null;
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