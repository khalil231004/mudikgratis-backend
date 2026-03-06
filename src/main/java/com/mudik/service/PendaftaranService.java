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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@ApplicationScoped
public class PendaftaranService {

    @ConfigProperty(name = "quarkus.http.body.uploads-directory")
    String uploadDir;

    @Inject
    WhatsAppService whatsAppService;

    // ================================================================
    // SATU ATURAN KUOTA — berlaku di seluruh sistem tanpa pengecualian
    //
    //  kuota_terisi HANYA berubah pada dua kejadian:
    //
    //   +1  saat peserta MASUK ke "DITERIMA H-3" atau "TERVERIFIKASI/ SIAP BERANGKAT"
    //       dari status yang BUKAN keduanya
    //
    //   -1  saat peserta KELUAR dari "DITERIMA H-3" atau "TERVERIFIKASI/ SIAP BERANGKAT"
    //       ke status apapun lainnya
    //
    //  Status di luar dua itu (MENUNGGU, PENDING, DITOLAK, DIBATALKAN)
    //  TIDAK PERNAH menyentuh kuota_terisi.
    //
    //  Semua peserta termasuk BAYI dihitung dalam kuota.
    // ================================================================

    /** true jika status ini termasuk "zona kuota" */
    private boolean pakaiKuota(String status) {
        return "DITERIMA H-3".equals(status)
                || "TERVERIFIKASI/ SIAP BERANGKAT".equals(status);
    }

    /**
     * Terapkan perubahan kuota akibat transisi status.
     * Dipanggil SEBELUM p.status_pendaftaran diubah.
     *
     *   lama=tidak pakai, baru=pakai  → +1
     *   lama=pakai,       baru=tidak  → -1
     *   lama=pakai,       baru=pakai  → 0  (DITERIMA → SIAP BERANGKAT, tidak berubah)
     *   lama=tidak pakai, baru=tidak  → 0  (MENUNGGU → DITOLAK, tidak berubah)
     */
    private void terapkanKuota(Rute rute, PendaftaranMudik p, String statusBaru) {
        boolean lamaZona = pakaiKuota(p.status_pendaftaran);
        boolean baruZona  = pakaiKuota(statusBaru);
        if (!lamaZona && baruZona) {
            if (rute.kuota_terisi == null) rute.kuota_terisi = 0;
            rute.kuota_terisi += 1;
        } else if (lamaZona && !baruZona) {
            if (rute.kuota_terisi != null && rute.kuota_terisi > 0)
                rute.kuota_terisi -= 1;
        }
    }

    private String hitungKategori(LocalDate tgl) {
        if (tgl == null) return "DEWASA";
        int umur = Period.between(tgl, LocalDate.now()).getYears();
        if (umur < 2)  return "BAYI";
        if (umur < 17) return "ANAK";
        return "DEWASA";
    }

    // ================================================================
    // 1. PENDAFTARAN WEB  →  MENUNGGU VERIFIKASI
    //    Tidak menyentuh kuota_terisi sama sekali.
    // ================================================================
    @Transactional
    public void prosesPendaftaranWeb(User user, Rute rute, PendaftaranMultipartForm form) throws Exception {
        int jumlahPeserta = form.nama_peserta.size();

        PortalConfig portalCfg = PortalConfig.getInstance();
        if (!Boolean.TRUE.equals(portalCfg.sesi_aktif))
            throw new Exception(portalCfg.pesan_sesi_berakhir != null ? portalCfg.pesan_sesi_berakhir : "Program Mudik Gratis telah berakhir.");
        if (!Boolean.TRUE.equals(portalCfg.portal_mudik_open))
            throw new Exception(portalCfg.pesan_mudik_tutup != null ? portalCfg.pesan_mudik_tutup : "Pendaftaran Mudik Gratis saat ini ditutup.");
        if (rute.is_portal_open != null && !rute.is_portal_open)
            throw new Exception("Portal pendaftaran untuk rute ini saat ini DITUTUP.");

        long sudahDaftar = PendaftaranMudik.count(
                "user.user_id = ?1 AND status_pendaftaran NOT IN ('DITOLAK', 'DIBATALKAN')", user.user_id);
        if (sudahDaftar + jumlahPeserta > 6)
            throw new Exception("Kuota akun penuh! Sisa slot anda: " + (6 - sudahDaftar));

        PendaftaranMudik existing = PendaftaranMudik.find(
                "user.user_id = ?1 AND status_pendaftaran NOT IN ('DITOLAK', 'DIBATALKAN')", user.user_id).firstResult();
        if (existing != null && !existing.rute.rute_id.equals(rute.rute_id))
            throw new Exception("Satu akun hanya boleh memilih 1 rute. Anda sudah terdaftar di rute: " + existing.rute.tujuan);

        if (form.nik_peserta != null) {
            List<String> duplikat = new ArrayList<>();
            for (String nik : form.nik_peserta) {
                String clean = nik.trim();
                if (PendaftaranMudik.count("nik_peserta = ?1 AND status_pendaftaran NOT IN ('DIBATALKAN','DITOLAK')", clean) > 0)
                    duplikat.add(clean);
            }
            if (!duplikat.isEmpty()) throw new Exception("NIK sudah terdaftar & aktif: " + String.join(", ", duplikat));
        }

        Rute ruteSave = Rute.findById(rute.rute_id);
        for (int i = 0; i < jumlahPeserta; i++) {
            PendaftaranMudik p = new PendaftaranMudik();
            p.user = user;
            p.rute = ruteSave;
            p.nama_peserta = form.nama_peserta.get(i).toUpperCase();
            p.uuid = UUID.randomUUID().toString();

            String rawNik = (form.nik_peserta != null && i < form.nik_peserta.size()) ? form.nik_peserta.get(i).trim() : "-";
            if (rawNik.length() > 16) rawNik = rawNik.substring(0, 16);
            p.nik_peserta = rawNik;

            try {
                if (form.tanggal_lahir != null && i < form.tanggal_lahir.size()) {
                    LocalDate tgl = LocalDate.parse(form.tanggal_lahir.get(i));
                    p.tanggal_lahir = tgl;
                    p.kategori_penumpang = hitungKategori(tgl);
                } else { p.tanggal_lahir = LocalDate.now(); p.kategori_penumpang = "DEWASA"; }
            } catch (Exception e) { p.tanggal_lahir = LocalDate.now(); p.kategori_penumpang = "DEWASA"; }

            p.jenis_kelamin = (form.jenis_kelamin != null && i < form.jenis_kelamin.size()) ? form.jenis_kelamin.get(i) : "L";
            p.alamat_rumah  = (form.alamat_rumah  != null && i < form.alamat_rumah.size())  ? form.alamat_rumah.get(i)  : "-";
            p.no_hp_peserta = (form.no_hp_peserta != null && i < form.no_hp_peserta.size()) ? form.no_hp_peserta.get(i) : null;

            if (form.fotoBukti != null && i < form.fotoBukti.size()) {
                FileUpload file = form.fotoBukti.get(i);
                if (file != null && file.fileName() != null && !file.fileName().isEmpty())
                    p.foto_identitas_path = uploadFileHelper(file, p.nik_peserta);
            }

            p.status_pendaftaran = "MENUNGGU VERIFIKASI";  // tidak sentuh kuota
            p.kode_booking = "MDK-" + System.currentTimeMillis() + "-" + (i + 1);
            p.persist();
        }
        // kuota_terisi TIDAK diubah — peserta masih di luar zona kuota
    }

    // ================================================================
    // 2. KONFIRMASI KEHADIRAN (oleh user)
    //    DITERIMA H-3 → SIAP BERANGKAT : pakai→pakai = 0
    //    DITERIMA H-3 → DIBATALKAN     : pakai→tidak = -1
    // ================================================================
    @Transactional
    public String prosesKonfirmasi(Long userId, List<Long> idsTetapIkut) throws Exception {
        List<PendaftaranMudik> keluarga = PendaftaranMudik.list(
                "user.user_id = ?1 AND status_pendaftaran = 'DITERIMA H-3'", userId);
        if (keluarga.isEmpty()) throw new Exception("Tidak ada data DITERIMA H-3.");

        Rute rute = Rute.findById(keluarga.get(0).rute.rute_id, LockModeType.PESSIMISTIC_WRITE);
        int hadir = 0, batal = 0;

        for (PendaftaranMudik p : keluarga) {
            String statusBaru = idsTetapIkut.contains(p.pendaftaran_id)
                    ? "TERVERIFIKASI/ SIAP BERANGKAT" : "DIBATALKAN";
            terapkanKuota(rute, p, statusBaru);
            p.status_pendaftaran = statusBaru;
            p.persist();
            if ("DIBATALKAN".equals(statusBaru)) batal++; else hadir++;
        }
        if (rute != null) rute.persist();
        return hadir + " Siap Berangkat, " + batal + " Dibatalkan.";
    }

    // ================================================================
    // 3. EDIT DATA OLEH USER (setelah DITOLAK)
    //    DITOLAK → MENUNGGU VERIFIKASI : tidak pakai → tidak pakai = 0
    // ================================================================
    @Transactional
    public void editPendaftaran(Long userId, Long pendaftaranId, PendaftaranMultipartForm form) throws Exception {
        PendaftaranMudik p = PendaftaranMudik.findById(pendaftaranId);
        if (p == null || !p.user.user_id.equals(userId)) throw new Exception("Data tidak valid.");
        if (!"DITOLAK".equals(p.status_pendaftaran)) throw new Exception("Hanya status DITOLAK yang bisa diperbaiki.");

        if (form.nama_peserta != null) p.nama_peserta = form.nama_peserta.get(0).toUpperCase();
        if (form.nik_peserta  != null) p.nik_peserta  = form.nik_peserta.get(0);

        LocalDate tglFinal = p.tanggal_lahir;
        if (form.tanggal_lahir != null && !form.tanggal_lahir.isEmpty()
                && form.tanggal_lahir.get(0) != null && !form.tanggal_lahir.get(0).isBlank()) {
            try { tglFinal = LocalDate.parse(form.tanggal_lahir.get(0)); p.tanggal_lahir = tglFinal; }
            catch (Exception ignored) {}
        }
        p.kategori_penumpang = hitungKategori(tglFinal);

        if (form.fotoBukti != null && !form.fotoBukti.isEmpty()) {
            FileUpload file = form.fotoBukti.get(0);
            if (file != null && file.fileName() != null && !file.fileName().isEmpty() && file.size() > 0)
                p.foto_identitas_path = uploadFileHelper(file, p.nik_peserta);
        }

        // DITOLAK → MENUNGGU: keduanya tidak di zona kuota → tidak berubah
        p.status_pendaftaran = "MENUNGGU VERIFIKASI";
        p.alasan_tolak = null;
        p.tolak_at = null;
        p.persist();

        long masihDitolak = PendaftaranMudik.count(
                "user.user_id = ?1 AND pendaftaran_id != ?2 AND status_pendaftaran = 'DITOLAK'", userId, pendaftaranId);
        if (masihDitolak == 0) {
            for (PendaftaranMudik anggota : PendaftaranMudik.<PendaftaranMudik>list("user.user_id = ?1", userId)) {
                if ("PENDING".equals(anggota.status_pendaftaran)) {
                    anggota.status_pendaftaran = "MENUNGGU VERIFIKASI";
                    anggota.persist();
                }
            }
        }
    }

    // ================================================================
    // 4. ADMIN: TOLAK PESERTA SATUAN
    //    Jika dari zona kuota → -1, jika tidak → 0
    // ================================================================
    @Transactional
    public void adminTolakPeserta(Long pendaftaranId) {
        PendaftaranMudik p = PendaftaranMudik.findById(pendaftaranId);
        if (p == null || "DITOLAK".equals(p.status_pendaftaran) || "DIBATALKAN".equals(p.status_pendaftaran)) return;

        if (p.rute != null) {
            Rute rute = Rute.findById(p.rute.rute_id, LockModeType.PESSIMISTIC_WRITE);
            terapkanKuota(rute, p, "DITOLAK");
            rute.persist();
        }
        p.status_pendaftaran = "DITOLAK";
        p.persist();
    }

    // ================================================================
    // 5. ADMIN: UPDATE STATUS KELUARGA (manual batch)
    //    Semua logika kuota lewat terapkanKuota()
    // ================================================================
    @Transactional
    public String updateStatusKeluarga(Long userId, String statusBaru, String alasan) throws Exception {
        List<PendaftaranMudik> keluarga = PendaftaranMudik.list("user.user_id = ?1", userId);
        if (keluarga.isEmpty()) throw new Exception("Data keluarga tidak ditemukan!");

        Rute rute = Rute.findById(keluarga.get(0).rute.rute_id, LockModeType.PESSIMISTIC_WRITE);

        for (PendaftaranMudik p : keluarga) {
            String statusLama = p.status_pendaftaran;

            // Status final (DITOLAK/DIBATALKAN) hanya bisa di-reset ke MENUNGGU,
            // atau jika target = status yang sama (idempotent, skip)
            boolean sudahFinal = "DITOLAK".equals(statusLama) || "DIBATALKAN".equals(statusLama);
            if (sudahFinal) {
                // Jika target bukan MENUNGGU dan bukan status yang sama → skip
                if (!"MENUNGGU VERIFIKASI".equalsIgnoreCase(statusBaru) && !statusLama.equals(statusBaru)) continue;
                // Jika target = status yang sama → skip (idempotent, kuota sudah benar)
                if (statusLama.equals(statusBaru)) continue;
            }

            // Guard kuota saat masuk DITERIMA H-3
            if ("DITERIMA H-3".equals(statusBaru) && !pakaiKuota(statusLama) && rute.getSisaKuota() <= 0)
                throw new Exception("Kuota rute sudah penuh! Tidak bisa terima: " + p.nama_peserta);

            terapkanKuota(rute, p, statusBaru);

            switch (statusBaru) {
                case "DITOLAK":
                    p.alasan_tolak = (alasan != null && !alasan.isBlank()) ? alasan : "Ditolak oleh admin";
                    p.tolak_at = null;
                    break;
                case "DIBATALKAN":
                    p.link_konfirmasi_dikirim = false;
                    if (p.kendaraan != null) {
                        p.kendaraan.terisi = Math.max(0, (p.kendaraan.terisi != null ? p.kendaraan.terisi : 0) - 1);
                        p.kendaraan.persist();
                        p.kendaraan = null;
                    }
                    break;
                case "DITERIMA H-3":
                    p.alasan_tolak = null; p.tolak_at = null;
                    break;
                case "MENUNGGU VERIFIKASI":
                    p.alasan_tolak = null; p.tolak_at = null;
                    p.link_konfirmasi_dikirim = false;
                    if (p.kendaraan != null) {
                        p.kendaraan.terisi = Math.max(0, (p.kendaraan.terisi != null ? p.kendaraan.terisi : 0) - 1);
                        p.kendaraan.persist();
                        p.kendaraan = null;
                    }
                    break;
            }

            p.status_pendaftaran = statusBaru;
            p.persist();
        }

        rute.persist();

        String tipeWa = "TERIMA";
        if ("DITOLAK".equalsIgnoreCase(statusBaru)) tipeWa = "TOLAK_DATA";
        else if ("DITERIMA H-3".equals(statusBaru)) tipeWa = "DITERIMA(H-3)";

        return whatsAppService.generateLink(getValidPhoneNumber(keluarga), tipeWa, keluarga.get(0), alasan);
    }

    // ================================================================
    // 6. ADMIN: TOLAK KELUARGA (batch)
    // ================================================================
    @Transactional
    public String tolakPendaftaranKeluarga(Long userId, String alasan) throws Exception {
        return updateStatusKeluarga(userId, "DITOLAK", alasan);
    }

    // ================================================================
    // 7. ADMIN: VERIFIKASI CUSTOM (checkbox per anggota)
    //    Jika ada ditolak  → anggota lain → PENDING (tidak sentuh kuota)
    //    Jika semua OK     → semua ke DITERIMA H-3 (+1 via terapkanKuota)
    // ================================================================
    @Transactional
    public String verifikasiCustom(Long userId, List<Long> idsDitolak, String alasan) throws Exception {
        List<PendaftaranMudik> keluarga = PendaftaranMudik.list("user.user_id = ?1", userId);
        if (keluarga.isEmpty()) throw new Exception("Data tidak ditemukan.");

        Rute rute = Rute.findById(keluarga.get(0).rute.rute_id, LockModeType.PESSIMISTIC_WRITE);
        int countDitolak = 0;

        for (PendaftaranMudik p : keluarga) {
            if ("DIBATALKAN".equals(p.status_pendaftaran)) continue;

            if (idsDitolak.contains(p.pendaftaran_id)) {
                // ke DITOLAK — terapkanKuota mengurus apakah perlu -1 atau tidak
                terapkanKuota(rute, p, "DITOLAK");
                p.status_pendaftaran = "DITOLAK";
                p.alasan_tolak = alasan;
                p.tolak_at = null;
                p.persist();
                countDitolak++;
            } else if ("DITOLAK".equals(p.status_pendaftaran)) {
                // Restore DITOLAK → MENUNGGU: tidak pakai → tidak pakai = 0
                terapkanKuota(rute, p, "MENUNGGU VERIFIKASI");
                p.status_pendaftaran = "MENUNGGU VERIFIKASI";
                p.alasan_tolak = null; p.tolak_at = null;
                p.persist();
            }
        }

        rute.persist();

        if (countDitolak > 0) {
            // Ada yang ditolak → sisa anggota jadi PENDING (tidak sentuh kuota)
            for (PendaftaranMudik p : keluarga) {
                if ("DIBATALKAN".equals(p.status_pendaftaran)) continue;
                if (!idsDitolak.contains(p.pendaftaran_id) && !"DITOLAK".equals(p.status_pendaftaran)) {
                    // MENUNGGU/PENDING → PENDING: tidak pakai → tidak pakai = 0
                    p.status_pendaftaran = "PENDING";
                    p.alasan_tolak = null;
                    p.persist();
                }
            }
        } else {
            // Semua OK → DITERIMA H-3 (+1 untuk yang belum di zona kuota)
            for (PendaftaranMudik p : keluarga) {
                if ("DIBATALKAN".equals(p.status_pendaftaran) || "DITOLAK".equals(p.status_pendaftaran)) continue;
                if (!pakaiKuota(p.status_pendaftaran) && rute.getSisaKuota() <= 0)
                    throw new Exception("Kuota rute penuh saat memproses: " + p.nama_peserta);
                terapkanKuota(rute, p, "DITERIMA H-3");
                p.status_pendaftaran = "DITERIMA H-3";
                p.alasan_tolak = null;
                p.persist();
            }
            // FIX: Hanya satu rute.persist() di sini, bukan dua
        }

        String tipeWa = countDitolak > 0 ? "TOLAK_DATA" : "DITERIMA(H-3)";
        String pesanAlasan = countDitolak > 0
                ? "Terdapat " + countDitolak + " data yang perlu diperbaiki (" + alasan + ")." : null;
        return whatsAppService.generateLink(getValidPhoneNumber(keluarga), tipeWa, keluarga.get(0), pesanAlasan);
    }

    // ================================================================
    // 8. GET PENDAFTAR PAGINATED (tidak ada logika kuota)
    // ================================================================
    public Map<String, Object> getPendaftarAdminPaginated(int page, int limit, String search, String rute, Long ruteId, String status, String sortOrder) {
        StringBuilder where = new StringBuilder("p.user IS NOT NULL");
        Map<String, Object> params = new HashMap<>();

        if (search != null && !search.trim().isEmpty()) {
            where.append(" AND (LOWER(p.nama_peserta) LIKE :search OR p.nik_peserta LIKE :search OR LOWER(p.user.nama_lengkap) LIKE :search)");
            params.put("search", "%" + search.trim().toLowerCase() + "%");
        }
        if (ruteId != null) { where.append(" AND p.rute.rute_id = :ruteId"); params.put("ruteId", ruteId); }
        else if (rute != null && !rute.trim().isEmpty()) { where.append(" AND p.rute.tujuan = :rute"); params.put("rute", rute.trim()); }
        if (status != null && !status.trim().isEmpty()) {
            if ("SIAP BERANGKAT".equals(status.trim()))
                where.append(" AND (p.status_pendaftaran LIKE '%SIAP BERANGKAT%' OR p.status_pendaftaran LIKE '%TERKONFIRMASI%')");
            else { where.append(" AND p.status_pendaftaran = :status"); params.put("status", status.trim()); }
        }

        var countQ = PendaftaranMudik.getEntityManager().createQuery(
                "SELECT COUNT(DISTINCT p.user.user_id) FROM PendaftaranMudik p WHERE " + where, Long.class);
        params.forEach(countQ::setParameter);
        long totalKeluarga = countQ.getSingleResult();
        int totalPages = totalKeluarga == 0 ? 1 : (int) Math.ceil((double) totalKeluarga / limit);

        String orderDir = "DESC".equalsIgnoreCase(sortOrder) ? "DESC" : "ASC";
        var userQ = PendaftaranMudik.getEntityManager().createQuery(
                "SELECT p.user.user_id FROM PendaftaranMudik p WHERE " + where
                        + " GROUP BY p.user.user_id ORDER BY MIN(p.created_at) " + orderDir, Long.class);
        params.forEach(userQ::setParameter);
        List<Long> userIds = userQ.setFirstResult((page - 1) * limit).setMaxResults(limit).getResultList();

        List<PendaftaranMudik> rows = new ArrayList<>();
        if (!userIds.isEmpty()) {
            var dataQ = PendaftaranMudik.getEntityManager().createQuery(
                    "SELECT p FROM PendaftaranMudik p LEFT JOIN FETCH p.user LEFT JOIN FETCH p.rute LEFT JOIN FETCH p.kendaraan"
                            + " WHERE p.user.user_id IN :ids ORDER BY p.user.user_id ASC, p.created_at " + orderDir,
                    PendaftaranMudik.class);
            dataQ.setParameter("ids", userIds);
            rows = dataQ.getResultList();
        }

        List<Map<String, Object>> mapped = new ArrayList<>();
        for (PendaftaranMudik p : rows) {
            Map<String, Object> m = new HashMap<>();
            m.put("id",                   p.pendaftaran_id);
            m.put("uuid",                 p.uuid               != null ? p.uuid               : "");
            m.put("nama_peserta",         p.nama_peserta       != null ? p.nama_peserta       : "");
            m.put("nik_peserta",          p.nik_peserta        != null ? p.nik_peserta        : "");
            m.put("jenis_kelamin",        p.jenis_kelamin      != null ? p.jenis_kelamin      : "");
            m.put("kategori",             p.kategori_penumpang != null ? p.kategori_penumpang : "");
            m.put("status",               p.status_pendaftaran != null ? p.status_pendaftaran : "UNKNOWN");
            m.put("kode_booking",         p.kode_booking       != null ? p.kode_booking       : "-");
            m.put("alasan_tolak",         p.alasan_tolak       != null ? p.alasan_tolak       : "-");
            m.put("id_keluarga",          p.user     != null ? p.user.user_id        : 0L);
            m.put("nama_kepala_keluarga", p.user     != null ? p.user.nama_lengkap   : "Tanpa Akun");
            m.put("rute_tujuan",          p.rute     != null ? p.rute.tujuan         : "Unknown");
            m.put("rute_id",              p.rute     != null ? p.rute.rute_id        : null);
            m.put("tgl_berangkat",        p.rute     != null ? p.rute.getFormattedDate() : "-");
            m.put("nama_bus",             p.kendaraan != null ? p.kendaraan.nama_armada : "Belum Plotting");
            String hp = (p.no_hp_peserta != null && p.no_hp_peserta.length() > 5)
                    ? p.no_hp_peserta : (p.user != null && p.user.no_hp != null ? p.user.no_hp : "");
            m.put("no_hp_target", hp);
            m.put("foto_bukti", (p.foto_identitas_path != null && !p.foto_identitas_path.isBlank())
                    ? "/uploads/" + new File(p.foto_identitas_path).getName() : null);
            m.put("created_at", p.created_at != null ? p.created_at.toString() : null);
            m.put("link_konfirmasi_dikirim", p.link_konfirmasi_dikirim);
            m.put("konfirmasi_kirim_count",  p.konfirmasi_kirim_count);
            mapped.add(m);
        }

        Map<String, Object> response = new HashMap<>();
        response.put("data", mapped);
        response.put("totalKeluarga", totalKeluarga);
        response.put("totalPages", totalPages);
        response.put("currentPage", page);
        return response;
    }

    // ================================================================
    // HELPERS
    // ================================================================
    private String getValidPhoneNumber(List<PendaftaranMudik> keluarga) {
        if (keluarga == null || keluarga.isEmpty()) return null;
        for (PendaftaranMudik p : keluarga)
            if (p.no_hp_peserta != null && p.no_hp_peserta.trim().length() > 7) return p.no_hp_peserta.trim();
        if (keluarga.get(0).user != null && keluarga.get(0).user.no_hp != null)
            return keluarga.get(0).user.no_hp.trim();
        return null;
    }

    private String uploadFileHelper(FileUpload fileUpload, String nik) throws IOException {
        File folder = new File(uploadDir);
        if (!folder.exists()) folder.mkdirs();
        String originalName = fileUpload.fileName();
        String ext = originalName.contains(".") ? originalName.substring(originalName.lastIndexOf(".")) : ".jpg";
        String newName = "ktp-" + nik + "-" + UUID.randomUUID().toString().substring(0, 5) + ext;
        File dest = new File(folder, newName);
        Files.move(fileUpload.uploadedFile(), dest.toPath(), StandardCopyOption.REPLACE_EXISTING);
        return "uploads/" + newName;
    }
}