package com.mudik.resource;

import com.mudik.model.Kendaraan;
import com.mudik.model.PendaftaranMudik;
import com.mudik.model.Rute;
import com.mudik.service.ExcelService;
import com.mudik.service.PendaftaranService;
import com.mudik.service.WhatsAppService;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Path("/api/admin")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class AdminResource {

    @Inject ExcelService excelService;
    @Inject WhatsAppService waService;
    @Inject PendaftaranService pendaftaranService;

    // ================================================================
    // ATURAN KUOTA (sama persis dengan PendaftaranService)
    //
    //   +1 saat masuk ke "DITERIMA H-3" atau "TERVERIFIKASI/ SIAP BERANGKAT"
    //      dari status yang BUKAN keduanya
    //
    //   -1 saat keluar dari "DITERIMA H-3" atau "TERVERIFIKASI/ SIAP BERANGKAT"
    //      ke status apapun lainnya
    //
    //   Status MENUNGGU, PENDING, DITOLAK, DIBATALKAN
    //   TIDAK PERNAH menyentuh kuota_terisi.
    //   Semua peserta termasuk BAYI dihitung dalam kuota.
    // ================================================================

    private boolean pakaiKuota(String status) {
        return "DITERIMA H-3".equals(status) || "TERVERIFIKASI/ SIAP BERANGKAT".equals(status);
    }

    /** Terapkan perubahan kuota. Dipanggil SEBELUM p.status_pendaftaran diubah. */
    private void terapkanKuota(Rute rute, PendaftaranMudik p, String statusBaru) {
        if (rute == null) return;
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

    // ================================================================
    // 1. GET SEMUA PENDAFTAR
    // ================================================================
    @GET
    @Path("/pendaftar")
    public Response getAllPendaftar(@QueryParam("rute_id") Long ruteId) {
        List<PendaftaranMudik> list = ruteId != null
                ? PendaftaranMudik.list("rute.rute_id = ?1 ORDER BY created_at ASC", ruteId)
                : PendaftaranMudik.list("ORDER BY created_at ASC");

        List<Map<String, Object>> result = list.stream().map(p -> {
            Map<String, Object> map = new HashMap<>();
            map.put("id", p.pendaftaran_id);
            map.put("uuid", p.uuid != null ? p.uuid : "");
            map.put("nama_peserta", p.nama_peserta);
            map.put("nik_peserta", p.nik_peserta);
            map.put("jenis_kelamin", p.jenis_kelamin);
            map.put("kategori", p.kategori_penumpang != null ? p.kategori_penumpang : "");
            map.put("status", p.status_pendaftaran != null ? p.status_pendaftaran : "UNKNOWN");
            map.put("kode_booking", p.kode_booking != null ? p.kode_booking : "-");
            map.put("alasan_tolak", p.alasan_tolak != null ? p.alasan_tolak : "-");
            map.put("id_keluarga", p.user != null ? p.user.user_id : 0);
            map.put("nama_kepala_keluarga", p.user != null ? p.user.nama_lengkap : "Tanpa Akun");
            map.put("rute_tujuan", p.rute != null ? p.rute.tujuan : "Unknown");
            map.put("tgl_berangkat", p.rute != null ? p.rute.getFormattedDate() : "-");
            map.put("nama_bus", p.kendaraan != null ? p.kendaraan.nama_armada : "Belum Plotting");
            String hp = (p.no_hp_peserta != null && p.no_hp_peserta.length() > 5) ? p.no_hp_peserta : (p.user != null ? p.user.no_hp : "");
            map.put("no_hp_target", hp);
            if (p.foto_identitas_path != null) map.put("foto_bukti", "/uploads/" + new File(p.foto_identitas_path).getName());
            map.put("link_wa_terima", waService.generateLink(hp, "TERIMA", p, null));
            map.put("link_wa_tolak",  waService.generateLink(hp, "TOLAK_DATA", p, "Data tidak valid"));
            map.put("link_wa_h3",     waService.generateLink(hp, "DITERIMA(H-3)", p, null));
            return map;
        }).collect(Collectors.toList());

        return Response.ok(result).build();
    }

    // ================================================================
    // 1b. GET PENDAFTAR PAGINATED
    // ================================================================
    @GET
    @Path("/pendaftar/paginated")
    public Response getPendaftarPaginated(
            @QueryParam("page") Integer page, @QueryParam("limit") Integer limit,
            @QueryParam("search") String search, @QueryParam("rute") String rute,
            @QueryParam("rute_id") Long ruteId, @QueryParam("status") String status,
            @QueryParam("sort") String sort) {
        try {
            int pageNum  = (page  != null && page  > 0) ? page  : 1;
            int limitNum = (limit != null && limit > 0) ? limit : 30;
            String sortOrder = "DESC".equalsIgnoreCase(sort) ? "DESC" : "ASC";
            return Response.ok(pendaftaranService.getPendaftarAdminPaginated(pageNum, limitNum, search, rute, ruteId, status, sortOrder)).build();
        } catch (Exception e) {
            e.printStackTrace();
            return Response.status(500).entity(Map.of("error", e.getMessage() != null ? e.getMessage() : "Internal error")).build();
        }
    }

    // ================================================================
    // 2. VERIFIKASI KELUARGA (batch)
    // ================================================================
    @PUT
    @Path("/verifikasi-keluarga/{userId}")
    public Response verifikasiKeluarga(@PathParam("userId") Long userId, Map<String, String> body) {
        try {
            String aksi   = body.get("status");
            String alasan = body.get("alasan_tolak");
            if (aksi == null) return Response.status(400).entity(Map.of("error", "Status wajib diisi")).build();

            String linkWa = (aksi.contains("TOLAK") || "DITOLAK".equals(aksi))
                    ? pendaftaranService.tolakPendaftaranKeluarga(userId, alasan)
                    : pendaftaranService.updateStatusKeluarga(userId, aksi, null);

            return Response.ok(Map.of("status", "BERHASIL", "pesan", "Proses Berhasil", "link_wa", linkWa)).build();
        } catch (Exception e) {
            return Response.status(400).entity(Map.of("error", e.getMessage())).build();
        }
    }

    // ================================================================
    // 3. VERIFIKASI INDIVIDUAL
    //    Semua transisi kuota lewat terapkanKuota() — konsisten
    // ================================================================
    @PUT
    @Path("/verifikasi/{id}")
    @Transactional
    public Response updateIndividual(@PathParam("id") Long id, Map<String, String> body) {
        try {
            String statusBaru = body.get("status");
            PendaftaranMudik p = PendaftaranMudik.findById(id);
            if (p == null) return Response.status(404).entity(Map.of("error", "Data tidak ditemukan")).build();

            String statusLama = p.status_pendaftaran;
            Long userId = (p.user != null) ? p.user.user_id : null;

            Rute rute = (p.rute != null)
                    ? Rute.findById(p.rute.rute_id, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE) : null;

            // ── RESET ke MENUNGGU VERIFIKASI ──
            if ("MENUNGGU VERIFIKASI".equals(statusBaru) &&
                    ("DITOLAK".equals(statusLama) || "DIBATALKAN".equals(statusLama) || "PENDING".equals(statusLama))) {

                // Keluarkan dari zona kuota jika perlu
                if (rute != null) terapkanKuota(rute, p, "MENUNGGU VERIFIKASI");

                p.alasan_tolak = null;
                p.link_konfirmasi_dikirim = false;
                if (p.kendaraan != null) {
                    p.kendaraan.terisi = Math.max(0, (p.kendaraan.terisi != null ? p.kendaraan.terisi : 0) - 1);
                    p.kendaraan.persist();
                    p.kendaraan = null;
                }

                // Jika tidak ada lagi yang DITOLAK di keluarga, pulihkan anggota PENDING
                if (userId != null) {
                    long masihDitolak = PendaftaranMudik.count(
                            "user.user_id = ?1 AND pendaftaran_id != ?2 AND status_pendaftaran = 'DITOLAK'", userId, id);
                    if (masihDitolak == 0) {
                        for (PendaftaranMudik anggota : PendaftaranMudik.<PendaftaranMudik>list("user.user_id = ?1", userId)) {
                            if ("PENDING".equals(anggota.status_pendaftaran)) {
                                anggota.status_pendaftaran = "MENUNGGU VERIFIKASI";
                                anggota.link_konfirmasi_dikirim = false;
                                anggota.persist();
                            }
                        }
                    }
                }

                // ── TOLAK INDIVIDUAL ──
            } else if ("DITOLAK".equals(statusBaru) && !"DITOLAK".equals(statusLama) && !"DIBATALKAN".equals(statusLama)) {
                if (rute != null) terapkanKuota(rute, p, "DITOLAK");
                p.alasan_tolak = body.getOrDefault("alasan", "Ditolak oleh admin");
                p.tolak_at = null;

                // Anggota keluarga lain yang aktif → PENDING (tidak sentuh kuota)
                if (userId != null) {
                    for (PendaftaranMudik anggota : PendaftaranMudik.<PendaftaranMudik>list("user.user_id = ?1", userId)) {
                        if (anggota.pendaftaran_id.equals(id)) continue;
                        String st = anggota.status_pendaftaran;
                        if (!"DITOLAK".equals(st) && !"DIBATALKAN".equals(st) && !"PENDING".equals(st)) {
                            anggota.status_pendaftaran = "PENDING";
                            anggota.persist();
                        }
                    }
                }

                // ── BATALKAN INDIVIDUAL ──
            } else if ("DIBATALKAN".equals(statusBaru) && !"DITOLAK".equals(statusLama) && !"DIBATALKAN".equals(statusLama)) {
                if (rute != null) terapkanKuota(rute, p, "DIBATALKAN");
                if (p.kendaraan != null && p.kendaraan.terisi != null && p.kendaraan.terisi > 0) {
                    p.kendaraan.terisi = Math.max(0, p.kendaraan.terisi - 1);
                    p.kendaraan.persist();
                    p.kendaraan = null;
                }
                p.link_konfirmasi_dikirim = false;

            } else {
                return Response.status(400).entity(Map.of("error", "Transisi status tidak valid: " + statusLama + " → " + statusBaru)).build();
            }

            if (rute != null) rute.persist();
            p.status_pendaftaran = statusBaru;
            p.persist();

            return Response.ok(Map.of("status", "BERHASIL", "pesan", "Status peserta diperbarui menjadi " + statusBaru)).build();
        } catch (Exception e) {
            return Response.status(400).entity(Map.of("error", e.getMessage())).build();
        }
    }

    // ================================================================
    // 4. PLOTTING BUS
    // ================================================================
    @POST
    @Path("/assign-bus")
    @Transactional
    public Response assignBus(@QueryParam("user_id") Long userId, @QueryParam("kendaraan_id") Long kendaraanId) {
        Kendaraan busBaru = Kendaraan.findById(kendaraanId);
        if (busBaru == null) return Response.status(404).entity(Map.of("error", "Bus tidak ditemukan")).build();

        List<PendaftaranMudik> keluarga = PendaftaranMudik.list(
                "user.user_id = ?1 AND status_pendaftaran = 'TERVERIFIKASI/ SIAP BERANGKAT'", userId);
        if (keluarga.isEmpty()) {
            long belumKonfirmasi = PendaftaranMudik.count("user.user_id = ?1 AND status_pendaftaran = 'DITERIMA H-3'", userId);
            if (belumKonfirmasi > 0)
                return Response.status(400).entity(Map.of("error", "User belum konfirmasi kehadiran.")).build();
            return Response.status(400).entity(Map.of("error", "Data tidak ditemukan atau belum SIAP.")).build();
        }

        PendaftaranMudik first = keluarga.get(0);
        if (first.rute != null && busBaru.rute != null && !first.rute.rute_id.equals(busBaru.rute.rute_id))
            return Response.status(400).entity(Map.of("error", "Bus tidak sesuai rute peserta.")).build();

        if (busBaru.terisi + keluarga.size() > busBaru.kapasitas_total)
            return Response.status(400).entity(Map.of("error", "Bus penuh! Sisa: " + (busBaru.kapasitas_total - busBaru.terisi))).build();

        int added = 0;
        for (PendaftaranMudik p : keluarga) {
            if (p.kendaraan != null && p.kendaraan.id.equals(busBaru.id)) continue;
            if (p.kendaraan != null) {
                p.kendaraan.terisi = Math.max(0, (p.kendaraan.terisi != null ? p.kendaraan.terisi : 0) - 1);
                p.kendaraan.persist();
            }
            p.kendaraan = busBaru;
            busBaru.terisi += 1;
            added++;
            p.persist();
        }
        if (added == 0) return Response.status(400).entity(Map.of("error", "Semua peserta sudah di-plotting ke bus ini.")).build();
        busBaru.persist();
        return Response.ok(Map.of("status", "BERHASIL", "pesan", "Keluarga masuk bus " + busBaru.nama_armada)).build();
    }

    // ================================================================
    // 4b. BATALKAN PLOTTING BUS
    // ================================================================
    @POST
    @Path("/batalkan-plotting/{userId}")
    @Transactional
    public Response batalkanPlotting(@PathParam("userId") Long userId) {
        List<PendaftaranMudik> keluarga = PendaftaranMudik.list(
                "user.user_id = ?1 AND kendaraan IS NOT NULL AND status_pendaftaran = 'TERVERIFIKASI/ SIAP BERANGKAT'", userId);
        if (keluarga.isEmpty()) return Response.status(400).entity(Map.of("error", "Tidak ada plotting aktif.")).build();

        Map<Long, Kendaraan> buses = new java.util.LinkedHashMap<>();
        for (PendaftaranMudik p : keluarga) if (p.kendaraan != null) buses.put(p.kendaraan.id, p.kendaraan);
        for (PendaftaranMudik p : keluarga) {
            if (p.kendaraan != null)
                p.kendaraan.terisi = Math.max(0, (p.kendaraan.terisi != null ? p.kendaraan.terisi : 0) - 1);
            p.kendaraan = null;
            p.persist();
        }
        for (Kendaraan bus : buses.values()) bus.persist();
        return Response.ok(Map.of("status", "BERHASIL", "pesan", "Plotting " + keluarga.size() + " peserta berhasil dibatalkan.")).build();
    }

    // ================================================================
    // 4c. KIRIM LINK KONFIRMASI
    // ================================================================
    @POST
    @Path("/kirim-link-konfirmasi/{userId}")
    @Transactional
    public Response kirimLinkKonfirmasi(@PathParam("userId") Long userId) {
        try {
            List<PendaftaranMudik> keluarga = PendaftaranMudik.list(
                    "user.user_id = ?1 AND status_pendaftaran = 'DITERIMA H-3'", userId);
            if (keluarga.isEmpty())
                return Response.status(400).entity(Map.of("error", "Tidak ada peserta DITERIMA H-3.")).build();

            PendaftaranMudik wakil = keluarga.get(0);
            String hp = (wakil.no_hp_peserta != null && wakil.no_hp_peserta.length() > 5)
                    ? wakil.no_hp_peserta : (wakil.user != null ? wakil.user.no_hp : "");
            for (PendaftaranMudik p : keluarga) { p.link_konfirmasi_dikirim = true; p.persist(); }
            return Response.ok(Map.of("status", "BERHASIL", "pesan", "Link konfirmasi berhasil dikirim.",
                    "link_wa", waService.generateLink(hp, "DITERIMA(H-3)", wakil, null))).build();
        } catch (Exception e) {
            return Response.status(400).entity(Map.of("error", e.getMessage())).build();
        }
    }

    // ================================================================
    // 5. STATISTIK DASHBOARD
    // ================================================================
    @GET
    @Path("/stats")
    public Response getDashboardStats() {
        long totalPendaftar = PendaftaranMudik.count();
        long totalDiterima  = PendaftaranMudik.count("status_pendaftaran = 'DITERIMA H-3'");
        long totalSiap      = PendaftaranMudik.count("status_pendaftaran = 'TERVERIFIKASI/ SIAP BERANGKAT'");
        long totalDitolak   = PendaftaranMudik.count("status_pendaftaran = 'DITOLAK'");
        long totalMenunggu  = PendaftaranMudik.count("status_pendaftaran = 'MENUNGGU VERIFIKASI'");

        List<Rute> rutes = Rute.listAll();
        long sisaKuotaGlobal = rutes.stream().mapToLong(Rute::getSisaKuota).sum();

        List<Map<String, Object>> statsRute = rutes.stream().map(r -> {
            Map<String, Object> m = new HashMap<>();
            m.put("rute_id",    r.rute_id);
            m.put("tujuan",     r.tujuan);
            m.put("sisa_kuota", r.getSisaKuota());
            m.put("terisi",     r.kuota_terisi != null ? r.kuota_terisi : 0);
            m.put("total_kursi", r.kuota_total);
            return m;
        }).collect(Collectors.toList());

        long totalFeedback = com.mudik.model.Feedback.count("disetujui = true");
        Double avgRating = null;
        try {
            avgRating = (Double) com.mudik.model.Feedback.getEntityManager()
                    .createQuery("SELECT AVG(f.rating) FROM Feedback f WHERE f.disetujui = true").getSingleResult();
        } catch (Exception ignored) {}

        Map<String, Object> resp = new HashMap<>();
        resp.put("total_masuk",      totalPendaftar);
        resp.put("total_diterima",   totalDiterima);
        resp.put("total_siap",       totalSiap);
        resp.put("total_ditolak",    totalDitolak);
        resp.put("total_menunggu",   totalMenunggu);
        resp.put("sisa_kuota_global", sisaKuotaGlobal);
        resp.put("total_rute",       rutes.size());
        resp.put("total_armada",     Kendaraan.count());
        resp.put("total_feedback",   totalFeedback);
        resp.put("rata_rata_rating", avgRating != null ? Math.round(avgRating * 10.0) / 10.0 : 0.0);
        resp.put("detail_rute",      statsRute);
        return Response.ok(resp).build();
    }

    // ================================================================
    // 6. EXPORT EXCEL
    // ================================================================
    @GET
    @Path("/export")
    @Produces("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    public Response exportExcel(@QueryParam("rute_id") Long ruteId, @QueryParam("hanya_plotting") Boolean hanyaPlotting) {
        try {
            List<PendaftaranMudik> list;
            if (Boolean.TRUE.equals(hanyaPlotting)) {
                list = ruteId != null
                        ? PendaftaranMudik.list("rute.rute_id = ?1 AND kendaraan IS NOT NULL ORDER BY kendaraan.nama_armada ASC, nama_peserta ASC", ruteId)
                        : PendaftaranMudik.list("kendaraan IS NOT NULL ORDER BY rute.tujuan ASC, kendaraan.nama_armada ASC, nama_peserta ASC");
            } else {
                list = ruteId != null
                        ? PendaftaranMudik.list("rute.rute_id = ?1 ORDER BY nama_peserta ASC", ruteId)
                        : PendaftaranMudik.list("ORDER BY rute.tujuan ASC, nama_peserta ASC");
            }
            String filename = Boolean.TRUE.equals(hanyaPlotting) ? "Rekap_Sudah_Plotting.xlsx" : "Rekap_Mudik_Semua.xlsx";
            return Response.ok(excelService.generateLaporanExcel(list))
                    .header("Content-Disposition", "attachment; filename=\"" + filename + "\"").build();
        } catch (IOException e) {
            return Response.serverError().entity(Map.of("error", "Gagal generate Excel")).build();
        }
    }

    // ================================================================
    // 6b. KONFIRMASI MANUAL OLEH ADMIN
    //     DITERIMA H-3 → SIAP BERANGKAT : pakai→pakai = 0 (tidak berubah)
    //     DITERIMA H-3 → DIBATALKAN     : pakai→tidak = -1
    // ================================================================
    @PUT
    @Path("/konfirmasi-manual/{userId}")
    @Transactional
    public Response konfirmasiManual(@PathParam("userId") Long userId, Map<String, Object> body) {
        try {
            List<PendaftaranMudik> keluarga = PendaftaranMudik.list("user.user_id = ?1", userId);
            if (keluarga.isEmpty()) return Response.status(404).entity(Map.of("error", "User tidak ditemukan")).build();

            List<Integer> rawIds = (List<Integer>) body.get("ids_konfirmasi");
            List<Long> idsKonfirmasi = rawIds != null
                    ? rawIds.stream().map(Integer::longValue).collect(Collectors.toList())
                    : keluarga.stream().map(p -> p.pendaftaran_id).collect(Collectors.toList());

            Rute rute = Rute.findById(keluarga.get(0).rute.rute_id, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
            int dikonfirmasi = 0, dibatalkan = 0;

            for (PendaftaranMudik p : keluarga) {
                String st = p.status_pendaftaran;
                boolean isDiterima = "DITERIMA H-3".equals(st);
                boolean isSiap = st != null && (st.contains("SIAP BERANGKAT") || st.contains("TERKONFIRMASI"));
                if (!isDiterima && !isSiap) continue;

                if (idsKonfirmasi.contains(p.pendaftaran_id)) {
                    if (isDiterima) {
                        // DITERIMA H-3 → SIAP BERANGKAT: pakai→pakai = 0 (tidak sentuh kuota)
                        terapkanKuota(rute, p, "TERVERIFIKASI/ SIAP BERANGKAT");
                        p.status_pendaftaran = "TERVERIFIKASI/ SIAP BERANGKAT";
                        p.persist();
                        dikonfirmasi++;
                    }
                } else {
                    if (!isSiap) {
                        // DITERIMA H-3 → DIBATALKAN: pakai→tidak = -1
                        terapkanKuota(rute, p, "DIBATALKAN");
                        p.status_pendaftaran = "DIBATALKAN";
                        p.persist();
                        dibatalkan++;
                    }
                }
            }
            if (rute != null) rute.persist();

            return Response.ok(Map.of("status", "BERHASIL",
                    "pesan", "Konfirmasi manual selesai. Dikonfirmasi: " + dikonfirmasi + ", Dibatalkan: " + dibatalkan,
                    "dikonfirmasi", dikonfirmasi, "dibatalkan", dibatalkan)).build();
        } catch (Exception e) {
            return Response.status(400).entity(Map.of("error", e.getMessage())).build();
        }
    }

    // ================================================================
    // 6c. BATALKAN PESERTA INDIVIDUAL
    //     Kuota hanya berkurang jika peserta memang sedang di zona kuota
    // ================================================================
    @PUT
    @Path("/batalkan-peserta/{id}")
    @Transactional
    public Response batalkanPeserta(@PathParam("id") Long id) {
        try {
            PendaftaranMudik p = PendaftaranMudik.findById(id);
            if (p == null) return Response.status(404).entity(Map.of("error", "Peserta tidak ditemukan")).build();
            if ("DIBATALKAN".equals(p.status_pendaftaran))
                return Response.status(400).entity(Map.of("error", "Peserta sudah dibatalkan")).build();

            Rute rute = (p.rute != null)
                    ? Rute.findById(p.rute.rute_id, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE) : null;

            // terapkanKuota otomatis handle: hanya -1 jika status lama = zona kuota
            if (rute != null) {
                terapkanKuota(rute, p, "DIBATALKAN");
                rute.persist();
            }

            if (p.kendaraan != null && p.kendaraan.terisi != null && p.kendaraan.terisi > 0) {
                p.kendaraan.terisi -= 1;
                p.kendaraan.persist();
                p.kendaraan = null;
            }

            p.status_pendaftaran = "DIBATALKAN";
            p.persist();

            return Response.ok(Map.of("status", "BERHASIL", "pesan", "Peserta " + p.nama_peserta + " berhasil dibatalkan")).build();
        } catch (Exception e) {
            return Response.status(400).entity(Map.of("error", e.getMessage())).build();
        }
    }

    // ================================================================
    // 7. VERIFIKASI CUSTOM (CHECKBOX) — delegasi ke Service
    // ================================================================
    @PUT
    @Path("/verifikasi-custom/{userId}")
    @Transactional
    public Response verifikasiCustom(@PathParam("userId") Long userId, Map<String, Object> body) {
        try {
            List<Integer> rawList = (List<Integer>) body.get("rejected_ids");
            String alasan = (String) body.get("alasan");
            if (rawList == null) return Response.status(400).entity(Map.of("error", "List ID wajib ada")).build();
            List<Long> rejectedIds = rawList.stream().map(Integer::longValue).collect(Collectors.toList());
            String linkWa = pendaftaranService.verifikasiCustom(userId, rejectedIds, alasan);
            return Response.ok(Map.of("status", "BERHASIL", "pesan", "Data berhasil diproses.", "link_wa", linkWa)).build();
        } catch (Exception e) {
            e.printStackTrace();
            return Response.status(400).entity(Map.of("error", e.getMessage())).build();
        }
    }

    // ================================================================
    // 8. TAMBAH PENUMPANG OLEH ADMIN
    //    Masuk sebagai MENUNGGU VERIFIKASI → tidak sentuh kuota
    // ================================================================
    @POST
    @Path("/tambah-penumpang")
    @Consumes(MediaType.APPLICATION_JSON)
    @Transactional
    public Response tambahPenumpangAdmin(Map<String, Object> body) {
        try {
            Long ruteId      = body.get("rute_id")      != null ? Long.parseLong(body.get("rute_id").toString())      : null;
            Long kendaraanId = body.get("kendaraan_id") != null ? Long.parseLong(body.get("kendaraan_id").toString()) : null;
            String namaPeserta     = (String) body.get("nama_peserta");
            String nikPeserta      = (String) body.get("nik_peserta");
            String jenisKelamin    = (String) body.getOrDefault("jenis_kelamin", "L");
            String tanggalLahirStr = (String) body.get("tanggal_lahir");
            String alamat          = (String) body.getOrDefault("alamat_rumah", "-");
            String noHp            = (String) body.get("no_hp_peserta");
            Long   userAkunId      = body.get("user_id") != null ? Long.parseLong(body.get("user_id").toString()) : null;

            if (ruteId == null || namaPeserta == null || nikPeserta == null)
                return Response.status(400).entity(Map.of("error", "rute_id, nama_peserta, nik_peserta wajib diisi")).build();

            com.mudik.model.Rute rute = com.mudik.model.Rute.findById(ruteId);
            if (rute == null) return Response.status(404).entity(Map.of("error", "Rute tidak ditemukan")).build();

            long cekNik = com.mudik.model.PendaftaranMudik.count(
                    "nik_peserta = ?1 AND status_pendaftaran NOT IN ('DIBATALKAN', 'DITOLAK')", nikPeserta.trim());
            if (cekNik > 0) return Response.status(400).entity(Map.of("error", "NIK sudah terdaftar: " + nikPeserta)).build();

            com.mudik.model.PendaftaranMudik p = new com.mudik.model.PendaftaranMudik();
            p.rute          = rute;
            p.nama_peserta  = namaPeserta.toUpperCase();
            p.nik_peserta   = nikPeserta.trim();
            p.jenis_kelamin = jenisKelamin;
            p.alamat_rumah  = alamat;
            p.no_hp_peserta = noHp;
            p.kode_booking  = "ADM-" + System.currentTimeMillis();
            p.uuid          = java.util.UUID.randomUUID().toString();

            if (tanggalLahirStr != null && !tanggalLahirStr.isBlank()) {
                try {
                    java.time.LocalDate tgl = java.time.LocalDate.parse(tanggalLahirStr);
                    p.tanggal_lahir = tgl;
                    int umur = java.time.Period.between(tgl, java.time.LocalDate.now()).getYears();
                    p.kategori_penumpang = umur < 2 ? "BAYI" : (umur < 17 ? "ANAK" : "DEWASA");
                } catch (Exception e2) { p.kategori_penumpang = "DEWASA"; }
            } else { p.kategori_penumpang = "DEWASA"; }

            if (userAkunId != null) {
                com.mudik.model.User user = com.mudik.model.User.findById(userAkunId);
                if (user != null) p.user = user;
            }

            if (kendaraanId != null) {
                com.mudik.model.Kendaraan bus = com.mudik.model.Kendaraan.findById(kendaraanId);
                if (bus != null) {
                    p.kendaraan = bus;
                    if (bus.terisi == null) bus.terisi = 0;
                    bus.terisi += 1;
                    bus.persist();
                }
            }

            // MENUNGGU VERIFIKASI → tidak sentuh kuota_terisi sama sekali
            p.status_pendaftaran = "MENUNGGU VERIFIKASI";
            p.persist();

            return Response.ok(Map.of("status", "BERHASIL", "pesan", "Penumpang " + namaPeserta + " berhasil ditambahkan")).build();
        } catch (Exception e) {
            e.printStackTrace();
            return Response.status(400).entity(Map.of("error", e.getMessage())).build();
        }
    }

    // ================================================================
    // 9. NOTIFIKASI KUOTA PENUH
    // ================================================================
    @POST
    @Path("/notif-kuota-penuh/{rute_id}")
    public Response notifKuotaPenuh(@PathParam("rute_id") Long ruteId) {
        try {
            com.mudik.model.Rute rute = com.mudik.model.Rute.findById(ruteId);
            if (rute == null) return Response.status(404).entity(Map.of("error", "Rute tidak ditemukan")).build();
            if (rute.getSisaKuota() > 0)
                return Response.status(400).entity(Map.of("error", "Kuota belum penuh (sisa: " + rute.getSisaKuota() + ")")).build();

            List<com.mudik.model.PendaftaranMudik> penumpang = com.mudik.model.PendaftaranMudik.list(
                    "rute.rute_id = ?1 AND status_pendaftaran NOT IN ('DIBATALKAN', 'DITOLAK')", ruteId);

            Set<String> hpSudahDikirim = new java.util.HashSet<>();
            List<String> links = new java.util.ArrayList<>();
            for (com.mudik.model.PendaftaranMudik p : penumpang) {
                String hp = (p.no_hp_peserta != null && p.no_hp_peserta.length() > 7)
                        ? p.no_hp_peserta : (p.user != null ? p.user.no_hp : null);
                if (hp != null && !hpSudahDikirim.contains(hp)) {
                    links.add(waService.generateKuotaPenuhLink(hp, p.nama_peserta, rute.tujuan));
                    hpSudahDikirim.add(hp);
                }
            }
            return Response.ok(Map.of("status", "BERHASIL", "pesan", "Siap kirim ke " + links.size() + " nomor", "wa_links", links)).build();
        } catch (Exception e) {
            return Response.status(400).entity(Map.of("error", e.getMessage())).build();
        }
    }

    // ================================================================
    // 10. RESYNC KUOTA — perbaiki data yang sudah terlanjur tidak sinkron
    //     Hitung ulang kuota_terisi dari jumlah aktual di database
    // ================================================================
    @POST
    @Path("/resync-kuota")
    @Transactional
    public Response resyncKuota(@QueryParam("rute_id") Long ruteIdParam) {
        try {
            List<Rute> rutes = ruteIdParam != null
                    ? List.of(Rute.findById(ruteIdParam, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE))
                    : Rute.listAll();

            List<Map<String, Object>> hasil = new java.util.ArrayList<>();
            for (Rute rute : rutes) {
                if (rute == null) continue;
                int kuotaLama = rute.kuota_terisi != null ? rute.kuota_terisi : 0;

                // Hitung dari DB: semua peserta di zona kuota (DITERIMA H-3 + SIAP BERANGKAT)
                long kuotaAktual = PendaftaranMudik.count(
                        "rute.rute_id = ?1 AND status_pendaftaran IN ('DITERIMA H-3', 'TERVERIFIKASI/ SIAP BERANGKAT')",
                        rute.rute_id);

                rute.kuota_terisi = (int) kuotaAktual;
                rute.persist();

                Map<String, Object> info = new HashMap<>();
                info.put("rute_id",   rute.rute_id);
                info.put("tujuan",    rute.tujuan);
                info.put("kuota_lama", kuotaLama);
                info.put("kuota_baru", (int) kuotaAktual);
                info.put("selisih",    (int) kuotaAktual - kuotaLama);
                hasil.add(info);
            }

            return Response.ok(Map.of("status", "BERHASIL",
                    "pesan", "Kuota berhasil di-resync dari data aktual database.",
                    "detail", hasil)).build();
        } catch (Exception e) {
            e.printStackTrace();
            return Response.status(500).entity(Map.of("error", e.getMessage())).build();
        }
    }
}