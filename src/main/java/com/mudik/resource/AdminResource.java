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

    // =================================================================
    // 1. DASHBOARD & DATA PENDAFTAR
    // =================================================================
    @GET
    @Path("/pendaftar")
    public Response getAllPendaftar(@QueryParam("rute_id") Long ruteId) {
        List<PendaftaranMudik> list;
        if (ruteId != null) {
            list = PendaftaranMudik.list("rute.rute_id = ?1 ORDER BY created_at DESC", ruteId);
        } else {
            list = PendaftaranMudik.list("ORDER BY created_at DESC");
        }

        List<Map<String, Object>> result = list.stream().map(p -> {
            Map<String, Object> map = new HashMap<>();
            map.put("id", p.pendaftaran_id);
            // 🔥 FIX NO 6: Kirim UUID ke Frontend (Pastikan DB sudah di-update)
            map.put("uuid", (p.uuid != null) ? p.uuid : "");
            map.put("nama_peserta", p.nama_peserta);
            map.put("nik_peserta", p.nik_peserta);
            map.put("jenis_kelamin", p.jenis_kelamin);
            map.put("kategori", (p.kategori_penumpang != null) ? p.kategori_penumpang : "");
            map.put("status", (p.status_pendaftaran != null) ? p.status_pendaftaran : "UNKNOWN");
            map.put("kode_booking", (p.kode_booking != null) ? p.kode_booking : "-");
            map.put("alasan_tolak", (p.alasan_tolak != null) ? p.alasan_tolak : "-");
            map.put("id_keluarga", (p.user != null) ? p.user.user_id : 0);
            map.put("nama_kepala_keluarga", (p.user != null) ? p.user.nama_lengkap : "Tanpa Akun");
            map.put("rute_tujuan", (p.rute != null) ? p.rute.tujuan : "Unknown");
            map.put("tgl_berangkat", (p.rute != null) ? p.rute.getFormattedDate() : "-");
            map.put("nama_bus", (p.kendaraan != null) ? p.kendaraan.nama_armada : "Belum Plotting");

            String targetHp = (p.no_hp_peserta != null && p.no_hp_peserta.length() > 5) ? p.no_hp_peserta : ((p.user != null) ? p.user.no_hp : "");
            map.put("no_hp_target", targetHp);

            if (p.foto_identitas_path != null) {
                map.put("foto_bukti", "/uploads/" + new File(p.foto_identitas_path).getName());
            }

            // Link WA Preview
            map.put("link_wa_terima", waService.generateLink(targetHp, "TERIMA", p, null));
            map.put("link_wa_tolak", waService.generateLink(targetHp, "TOLAK_DATA", p, "Data tidak valid (Preview)"));
            map.put("link_wa_h3", waService.generateLink(targetHp, "DITERIMA(H-3)", p, null));

            return map;
        }).collect(Collectors.toList());

        return Response.ok(result).build();
    }


    // =================================================================
    // 1b. GET PENDAFTAR PAGINATED (UNTUK TABEL ADMIN)
    // =================================================================
    @GET
    @Path("/pendaftar/paginated")
    public Response getPendaftarPaginated(
            @QueryParam("page")   Integer page,
            @QueryParam("limit")  Integer limit,
            @QueryParam("search") String search,
            @QueryParam("rute")   String rute,
            @QueryParam("status") String status) {
        try {
            int pageNum  = (page  != null && page  > 0) ? page  : 1;
            int limitNum = (limit != null && limit > 0) ? limit : 30;

            // Service sudah mapping ke DTO - tinggal return
            Map<String, Object> result = pendaftaranService
                    .getPendaftarAdminPaginated(pageNum, limitNum, search, rute, status);

            return Response.ok(result).build();
        } catch (Exception e) {
            e.printStackTrace();
            return Response.status(500).entity(Map.of("error", e.getMessage() != null ? e.getMessage() : "Internal error")).build();
        }
    }

    // =================================================================
    // 2. VERIFIKASI KELUARGA (BATCH)
    // =================================================================
    @PUT
    @Path("/verifikasi-keluarga/{userId}")
    public Response verifikasiKeluarga(@PathParam("userId") Long userId, Map<String, String> body) {
        try {
            String aksi = body.get("status");
            String alasan = body.get("alasan_tolak");

            if (aksi == null) return Response.status(400).entity(Map.of("error", "Status wajib diisi")).build();

            String linkWa = "";

            // A. KASUS TOLAK (Pakai Service Khusus)
            if (aksi.contains("TOLAK") || "DITOLAK".equals(aksi)) {
                linkWa = pendaftaranService.tolakPendaftaranKeluarga(userId, alasan);
            }
            // B. KASUS RESET / TERIMA (Pakai Update Status Keluarga)
            else {
                // Method ini di Service sudah handle Reset Kuota (Menunggu) dan Terima (H-3)
                linkWa = pendaftaranService.updateStatusKeluarga(userId, aksi, null);
            }

            return Response.ok(Map.of("status", "BERHASIL", "pesan", "Proses Berhasil", "link_wa", linkWa)).build();
        } catch (Exception e) {
            return Response.status(400).entity(Map.of("error", e.getMessage())).build();
        }
    }

    // =================================================================
    // 3. VERIFIKASI INDIVIDUAL — DENGAN LOCK KELUARGA OTOMATIS
    // =================================================================
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

            // ── RESET: dari DITOLAK/BATAL/PENDING ke MENUNGGU ──
            if ("MENUNGGU VERIFIKASI".equals(statusBaru) &&
                    ("DITOLAK".equals(statusLama) || "DIBATALKAN".equals(statusLama) || "PENDING".equals(statusLama))) {

                // Hanya kembalikan kuota jika sebelumnya DITOLAK/DIBATALKAN (PENDING tidak mengurangi kuota)
                if ("DITOLAK".equals(statusLama) || "DIBATALKAN".equals(statusLama)) {
                    if (p.rute.getSisaKuota() <= 0) {
                        return Response.status(400).entity(Map.of("error", "Kuota Rute Penuh, tidak bisa reset data!")).build();
                    }
                    p.rute.kuota_terisi = (p.rute.kuota_terisi == null ? 0 : p.rute.kuota_terisi) + 1;
                }
                p.alasan_tolak = null;

                // Cek apakah setelah reset ini masih ada yang ditolak di keluarga
                // Jika tidak → pulihkan semua PENDING ke MENUNGGU VERIFIKASI
                if (userId != null) {
                    long masihDitolak = PendaftaranMudik.count(
                            "user.user_id = ?1 AND pendaftaran_id != ?2 AND status_pendaftaran = 'DITOLAK'",
                            userId, id
                    );
                    if (masihDitolak == 0) {
                        List<PendaftaranMudik> keluarga = PendaftaranMudik.list("user.user_id = ?1", userId);
                        for (PendaftaranMudik anggota : keluarga) {
                            if ("PENDING".equals(anggota.status_pendaftaran)) {
                                anggota.status_pendaftaran = "MENUNGGU VERIFIKASI";
                                anggota.persist();
                            }
                        }
                    }
                }
            }

            // ── TOLAK INDIVIDUAL: kurangi kuota + set anggota lain ke PENDING ──
            else if ("DITOLAK".equals(statusBaru) &&
                    !"DITOLAK".equals(statusLama) && !"DIBATALKAN".equals(statusLama)) {

                boolean wasActive = !"PENDING".equals(statusLama);
                if (wasActive && !"BAYI".equalsIgnoreCase(p.kategori_penumpang) && p.rute.kuota_terisi > 0) {
                    p.rute.kuota_terisi -= 1;
                }
                p.alasan_tolak = body.getOrDefault("alasan", "Ditolak oleh admin");

                // Set anggota keluarga lain yang masih aktif ke PENDING
                if (userId != null) {
                    List<PendaftaranMudik> keluarga = PendaftaranMudik.list("user.user_id = ?1", userId);
                    for (PendaftaranMudik anggota : keluarga) {
                        if (anggota.pendaftaran_id.equals(id)) continue;
                        String st = anggota.status_pendaftaran;
                        if (!"DITOLAK".equals(st) && !"DIBATALKAN".equals(st) && !"PENDING".equals(st)) {
                            anggota.status_pendaftaran = "PENDING";
                            anggota.persist();
                        }
                    }
                }
            }

            // ── BATAL: kembalikan kuota ──
            else if ("DIBATALKAN".equals(statusBaru) &&
                    !"DITOLAK".equals(statusLama) && !"DIBATALKAN".equals(statusLama)) {

                if (!"BAYI".equalsIgnoreCase(p.kategori_penumpang) && p.rute.kuota_terisi > 0) {
                    p.rute.kuota_terisi -= 1;
                }
            }

            p.status_pendaftaran = statusBaru;
            p.persist();

            return Response.ok(Map.of("status", "BERHASIL", "pesan", "Status peserta diperbarui menjadi " + statusBaru)).build();
        } catch (Exception e) {
            return Response.status(400).entity(Map.of("error", e.getMessage())).build();
        }
    }

    // =================================================================
    // 4. PLOTTING BUS
    // =================================================================
    @POST
    @Path("/assign-bus")
    @Transactional
    public Response assignBus(@QueryParam("user_id") Long userId, @QueryParam("kendaraan_id") Long kendaraanId) {
        // 1. Cek Bus Baru
        Kendaraan busBaru = Kendaraan.findById(kendaraanId);
        if (busBaru == null) return Response.status(404).entity(Map.of("error", "Bus tidak ditemukan")).build();

        // 2. Cek Status Wajib TERVERIFIKASI/ SIAP BERANGKAT
        List<PendaftaranMudik> keluarga = PendaftaranMudik.list("user.user_id = ?1 AND status_pendaftaran = 'TERVERIFIKASI/ SIAP BERANGKAT'", userId);

        if (keluarga.isEmpty()) {
            long belumKonfirmasi = PendaftaranMudik.count("user.user_id = ?1 AND status_pendaftaran = 'DITERIMA H-3'", userId);
            if (belumKonfirmasi > 0) {
                return Response.status(400).entity(Map.of("error", "User belum Konfirmasi Kehadiran (Link WA).")).build();
            }
            return Response.status(400).entity(Map.of("error", "Data tidak ditemukan atau belum SIAP.")).build();
        }

        // 2b. Validasi Rute: Bus harus sesuai rute peserta
        PendaftaranMudik firstPeserta = keluarga.get(0);
        if (firstPeserta.rute != null && busBaru.rute != null) {
            if (!firstPeserta.rute.rute_id.equals(busBaru.rute.rute_id)) {
                return Response.status(400).entity(Map.of(
                        "error", "Bus ini tidak sesuai rute! Peserta terdaftar di rute '"
                                + firstPeserta.rute.tujuan + "', sedangkan bus ini untuk rute '"
                                + busBaru.rute.tujuan + "'. Pilih bus yang sesuai rute peserta."
                )).build();
            }
        }

        // 3. Cek Kapasitas
        if (busBaru.terisi + keluarga.size() > busBaru.kapasitas_total) {
            return Response.status(400).entity(Map.of("error", "Bus Penuh! Sisa: " + (busBaru.kapasitas_total - busBaru.terisi))).build();
        }

        // 4. Proses Plotting / Pindah Bus
        for (PendaftaranMudik p : keluarga) {
            if (p.kendaraan != null && !p.kendaraan.id.equals(busBaru.id)) {
                p.kendaraan.terisi -= 1;
                p.kendaraan.persist();
            }
            p.kendaraan = busBaru;
            busBaru.terisi += 1;
            p.persist();
        }
        busBaru.persist();

        return Response.ok(Map.of("status", "BERHASIL", "pesan", "Keluarga masuk bus " + busBaru.nama_armada)).build();
    }

    // =================================================================
    // 4b. KIRIM LINK KONFIRMASI (Admin — set flag agar user bisa konfirmasi)
    // =================================================================
    @POST
    @Path("/kirim-link-konfirmasi/{userId}")
    @Transactional
    public Response kirimLinkKonfirmasi(@PathParam("userId") Long userId) {
        try {
            List<PendaftaranMudik> keluarga = PendaftaranMudik.list(
                    "user.user_id = ?1 AND status_pendaftaran = 'DITERIMA H-3'", userId);

            if (keluarga.isEmpty()) {
                return Response.status(400).entity(Map.of(
                        "error", "Tidak ada peserta dengan status DITERIMA H-3 untuk keluarga ini."
                )).build();
            }

            // Set flag link_konfirmasi_dikirim = true untuk semua anggota keluarga
            PendaftaranMudik wakil = keluarga.get(0);
            String targetHp = (wakil.no_hp_peserta != null && wakil.no_hp_peserta.length() > 5)
                    ? wakil.no_hp_peserta
                    : (wakil.user != null ? wakil.user.no_hp : "");

            for (PendaftaranMudik p : keluarga) {
                p.link_konfirmasi_dikirim = true;
                p.persist();
            }

            // Generate link WA konfirmasi
            String linkWa = waService.generateLink(targetHp, "DITERIMA(H-3)", wakil, null);

            return Response.ok(Map.of(
                    "status", "BERHASIL",
                    "pesan", "Link konfirmasi berhasil dikirim ke " + keluarga.size() + " peserta.",
                    "link_wa", linkWa
            )).build();
        } catch (Exception e) {
            return Response.status(400).entity(Map.of("error", e.getMessage())).build();
        }
    }

    // =================================================================
    // 5. STATISTIK
    // =================================================================
    @GET
    @Path("/stats")
    public Response getDashboardStats() {
        long totalPendaftar = PendaftaranMudik.count();
        long totalDiterima = PendaftaranMudik.count("status_pendaftaran = 'DITERIMA H-3'");
        long totalSiap = PendaftaranMudik.count("status_pendaftaran = 'TERVERIFIKASI/ SIAP BERANGKAT'");
        long totalDitolak = PendaftaranMudik.count("status_pendaftaran = 'DITOLAK'");
        long totalMenunggu = PendaftaranMudik.count("status_pendaftaran = 'MENUNGGU VERIFIKASI'");

        List<Rute> rutes = Rute.listAll();
        long totalRute = rutes.size();
        long sisaKuotaGlobal = 0;
        for(Rute r : rutes) sisaKuotaGlobal += r.getSisaKuota();

        // Total armada bus
        long totalArmada = Kendaraan.count();

        List<Map<String, Object>> statsRute = rutes.stream().map(rute -> {
            Map<String, Object> map = new HashMap<>();
            map.put("tujuan", rute.tujuan);
            map.put("sisa_kuota", rute.getSisaKuota());
            map.put("terisi", (rute.kuota_terisi != null ? rute.kuota_terisi : 0));
            map.put("total_kursi", rute.kuota_total);
            return map;
        }).collect(Collectors.toList());

        // Statistik feedback/kepuasan
        long totalFeedback = com.mudik.model.Feedback.count("disetujui = true");
        Double avgRating = null;
        try {
            avgRating = (Double) com.mudik.model.Feedback.getEntityManager()
                    .createQuery("SELECT AVG(f.rating) FROM Feedback f WHERE f.disetujui = true")
                    .getSingleResult();
        } catch (Exception ignored) {}

        // 🔥 FIX: Pakai HashMap untuk respon lebih dari 10 key
        Map<String, Object> responseData = new HashMap<>();
        responseData.put("total_masuk", totalPendaftar);
        responseData.put("total_diterima", totalDiterima);
        responseData.put("total_siap", totalSiap);
        responseData.put("total_ditolak", totalDitolak);
        responseData.put("total_menunggu", totalMenunggu);
        responseData.put("sisa_kuota_global", sisaKuotaGlobal);
        responseData.put("total_rute", totalRute);
        responseData.put("total_armada", totalArmada);
        responseData.put("total_feedback", totalFeedback);
        responseData.put("rata_rata_rating", avgRating != null ? Math.round(avgRating * 10.0) / 10.0 : 0.0);
        responseData.put("detail_rute", statsRute);

        return Response.ok(responseData).build();
    }

    // =================================================================
    // 6. EXPORT EXCEL nya
    // =================================================================
    @GET
    @Path("/export")
    @Produces("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    public Response exportExcel(@QueryParam("rute_id") Long ruteId, @QueryParam("hanya_plotting") Boolean hanyaPlotting) {
        try {
            List<PendaftaranMudik> list;
            // POIN 1: Jika hanya_plotting=true, filter hanya yang sudah di-assign ke bus
            if (Boolean.TRUE.equals(hanyaPlotting)) {
                if (ruteId != null) {
                    list = PendaftaranMudik.list(
                            "rute.rute_id = ?1 AND kendaraan IS NOT NULL ORDER BY kendaraan.nama_armada ASC, nama_peserta ASC", ruteId);
                } else {
                    list = PendaftaranMudik.list(
                            "kendaraan IS NOT NULL ORDER BY rute.tujuan ASC, kendaraan.nama_armada ASC, nama_peserta ASC");
                }
            } else {
                if (ruteId != null) {
                    list = PendaftaranMudik.list("rute.rute_id = ?1 ORDER BY nama_peserta ASC", ruteId);
                } else {
                    list = PendaftaranMudik.list("ORDER BY rute.tujuan ASC, nama_peserta ASC");
                }
            }
            String filename = Boolean.TRUE.equals(hanyaPlotting) ? "Rekap_Sudah_Plotting.xlsx" : "Rekap_Mudik_Semua.xlsx";
            return Response.ok(excelService.generateLaporanExcel(list))
                    .header("Content-Disposition", "attachment; filename=\"" + filename + "\"").build();
        } catch (IOException e) {
            return Response.serverError().entity(Map.of("error", "Gagal generate Excel")).build();
        }
    }

    // =================================================================
    // 6b. KONFIRMASI MANUAL OLEH ADMIN (POIN 6 - TOMBOL DARURAT)
    // =================================================================
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

            int dikonfirmasi = 0;
            int dibatalkan = 0;

            for (PendaftaranMudik p : keluarga) {
                String st = p.status_pendaftaran;
                boolean isDiterima = "DITERIMA H-3".equals(st) || st.contains("DITERIMA");
                boolean isSiap = st.contains("SIAP BERANGKAT") || st.contains("TERKONFIRMASI");
                if (!isDiterima && !isSiap) continue;

                if (idsKonfirmasi.contains(p.pendaftaran_id)) {
                    if (isDiterima) {
                        p.status_pendaftaran = "TERVERIFIKASI/ SIAP BERANGKAT";
                        p.persist();
                        dikonfirmasi++;
                    }
                } else {
                    // Batalkan
                    if (!isSiap) { // jangan batalkan yang sudah SIAP jika tidak diminta
                        if (p.rute != null && p.rute.kuota_terisi != null && p.rute.kuota_terisi > 0) {
                            p.rute.kuota_terisi -= 1;
                            p.rute.persist();
                        }
                        p.status_pendaftaran = "DIBATALKAN";
                        p.persist();
                        dibatalkan++;
                    }
                }
            }

            return Response.ok(Map.of(
                    "status", "BERHASIL",
                    "pesan", "Konfirmasi manual selesai. Dikonfirmasi: " + dikonfirmasi + ", Dibatalkan: " + dibatalkan,
                    "dikonfirmasi", dikonfirmasi,
                    "dibatalkan", dibatalkan
            )).build();
        } catch (Exception e) {
            return Response.status(400).entity(Map.of("error", e.getMessage())).build();
        }
    }

    // =================================================================
    // 6c. BATALKAN PER ORANG (POIN 9 - Batalkan individual tanpa ubah status lain)
    // =================================================================
    @PUT
    @Path("/batalkan-peserta/{id}")
    @Transactional
    public Response batalkanPeserta(@PathParam("id") Long id) {
        try {
            PendaftaranMudik p = PendaftaranMudik.findById(id);
            if (p == null) return Response.status(404).entity(Map.of("error", "Peserta tidak ditemukan")).build();

            // Jika sudah DIBATALKAN, jangan ubah apapun
            if ("DIBATALKAN".equals(p.status_pendaftaran)) {
                return Response.status(400).entity(Map.of("error", "Peserta sudah dibatalkan")).build();
            }

            // Kembalikan kuota rute (kecuali BAYI dan yang sudah DITOLAK)
            if (!"BAYI".equalsIgnoreCase(p.kategori_penumpang) &&
                    !"DITOLAK".equals(p.status_pendaftaran) &&
                    p.rute != null && p.rute.kuota_terisi != null && p.rute.kuota_terisi > 0) {
                p.rute.kuota_terisi -= 1;
                p.rute.persist();
            }

            // Kembalikan kursi bus jika sudah plotting
            if (p.kendaraan != null && p.kendaraan.terisi != null && p.kendaraan.terisi > 0) {
                p.kendaraan.terisi -= 1;
                p.kendaraan.persist();
                p.kendaraan = null;
            }

            // HANYA ubah status peserta ini, tidak sentuh anggota keluarga lain
            p.status_pendaftaran = "DIBATALKAN";
            p.persist();

            return Response.ok(Map.of(
                    "status", "BERHASIL",
                    "pesan", "Peserta " + p.nama_peserta + " berhasil dibatalkan"
            )).build();
        } catch (Exception e) {
            return Response.status(400).entity(Map.of("error", e.getMessage())).build();
        }
    }

    // 7. VERIFIKASI CUSTOM (CHECKBOX)
    @PUT
    @Path("/verifikasi-custom/{userId}")
    @Transactional
    public Response verifikasiCustom(@PathParam("userId") Long userId, Map<String, Object> body) {
        try {
            List<Integer> rawList = (List<Integer>) body.get("rejected_ids");
            String alasan = (String) body.get("alasan");

            if (rawList == null) return Response.status(400).entity(Map.of("error", "List ID wajib ada")).build();

            List<Long> rejectedIds = rawList.stream().map(Integer::longValue).collect(Collectors.toList());

            // 🔥 INI PENTING: Tangkap Link WA dari Service
            String linkWa = pendaftaranService.verifikasiCustom(userId, rejectedIds, alasan);

            // 🔥 DAN MASUKKAN KE SINI (Biar Frontend bisa baca)
            return Response.ok(Map.of(
                    "status", "BERHASIL",
                    "pesan", "Data berhasil diproses.",
                    "link_wa", linkWa
            )).build();

        } catch (Exception e) {
            e.printStackTrace();
            return Response.status(400).entity(Map.of("error", e.getMessage())).build();
        }
    }

    // =================================================================
    // FIX 8: TAMBAH PENUMPANG OLEH ADMIN (tanpa batasan akun user)
    // =================================================================
    @POST
    @Path("/tambah-penumpang")
    @Consumes(MediaType.APPLICATION_JSON)
    @Transactional
    public Response tambahPenumpangAdmin(Map<String, Object> body) {
        try {
            Long ruteId = body.get("rute_id") != null ? Long.parseLong(body.get("rute_id").toString()) : null;
            Long kendaraanId = body.get("kendaraan_id") != null ? Long.parseLong(body.get("kendaraan_id").toString()) : null;
            String namaPeserta = (String) body.get("nama_peserta");
            String nikPeserta = (String) body.get("nik_peserta");
            String jenisKelamin = (String) body.getOrDefault("jenis_kelamin", "L");
            String tanggalLahirStr = (String) body.get("tanggal_lahir");
            String alamat = (String) body.getOrDefault("alamat_rumah", "-");
            String noHp = (String) body.get("no_hp_peserta");
            Long userAkunId = body.get("user_id") != null ? Long.parseLong(body.get("user_id").toString()) : null;

            if (ruteId == null || namaPeserta == null || nikPeserta == null) {
                return Response.status(400).entity(Map.of("error", "rute_id, nama_peserta, nik_peserta wajib diisi")).build();
            }

            com.mudik.model.Rute rute = com.mudik.model.Rute.findById(ruteId, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
            if (rute == null) return Response.status(404).entity(Map.of("error", "Rute tidak ditemukan")).build();
            if (rute.getSisaKuota() <= 0) return Response.status(400).entity(Map.of("error", "Kuota rute penuh!")).build();

            // Cek duplikat NIK
            long cekNik = com.mudik.model.PendaftaranMudik.count("nik_peserta = ?1 AND status_pendaftaran NOT IN ('DIBATALKAN', 'DITOLAK')", nikPeserta.trim());
            if (cekNik > 0) return Response.status(400).entity(Map.of("error", "NIK sudah terdaftar: " + nikPeserta)).build();

            com.mudik.model.PendaftaranMudik p = new com.mudik.model.PendaftaranMudik();
            p.rute = rute;
            p.nama_peserta = namaPeserta.toUpperCase();
            p.nik_peserta = nikPeserta.trim();
            p.jenis_kelamin = jenisKelamin;
            p.alamat_rumah = alamat;
            p.no_hp_peserta = noHp;
            p.status_pendaftaran = "MENUNGGU VERIFIKASI";
            p.kode_booking = "ADM-" + System.currentTimeMillis();
            p.uuid = java.util.UUID.randomUUID().toString();

            if (tanggalLahirStr != null && !tanggalLahirStr.isBlank()) {
                try {
                    java.time.LocalDate tgl = java.time.LocalDate.parse(tanggalLahirStr);
                    p.tanggal_lahir = tgl;
                    int umur = java.time.Period.between(tgl, java.time.LocalDate.now()).getYears();
                    p.kategori_penumpang = umur < 2 ? "BAYI" : (umur < 17 ? "ANAK" : "DEWASA");
                } catch (Exception e2) { p.kategori_penumpang = "DEWASA"; }
            } else { p.kategori_penumpang = "DEWASA"; }

            // Assign ke user akun jika ada
            if (userAkunId != null) {
                com.mudik.model.User user = com.mudik.model.User.findById(userAkunId);
                if (user != null) p.user = user;
            }

            // Assign bus jika ada
            if (kendaraanId != null) {
                com.mudik.model.Kendaraan bus = com.mudik.model.Kendaraan.findById(kendaraanId);
                if (bus != null) {
                    p.kendaraan = bus;
                    if (bus.terisi == null) bus.terisi = 0;
                    bus.terisi += 1;
                    bus.persist();
                }
            }

            p.persist();
            rute.kuota_terisi = (rute.kuota_terisi == null ? 0 : rute.kuota_terisi) + 1;
            rute.persist();

            return Response.ok(Map.of("status", "BERHASIL", "pesan", "Penumpang " + namaPeserta + " berhasil ditambahkan oleh admin")).build();
        } catch (Exception e) {
            e.printStackTrace();
            return Response.status(400).entity(Map.of("error", e.getMessage())).build();
        }
    }

    // =================================================================
    // FIX 10: NOTIFIKASI KUOTA PENUH KE SEMUA PENUMPANG RUTE
    // =================================================================
    @POST
    @Path("/notif-kuota-penuh/{rute_id}")
    public Response notifKuotaPenuh(@PathParam("rute_id") Long ruteId) {
        try {
            com.mudik.model.Rute rute = com.mudik.model.Rute.findById(ruteId);
            if (rute == null) return Response.status(404).entity(Map.of("error", "Rute tidak ditemukan")).build();

            if (rute.getSisaKuota() > 0) {
                return Response.status(400).entity(Map.of("error", "Kuota rute belum penuh (sisa: " + rute.getSisaKuota() + ")")).build();
            }

            List<com.mudik.model.PendaftaranMudik> penumpang = com.mudik.model.PendaftaranMudik.list(
                    "rute.rute_id = ?1 AND status_pendaftaran NOT IN ('DIBATALKAN', 'DITOLAK')", ruteId);

            // Generate WA links untuk setiap penumpang (atau per keluarga)
            Set<String> hpSudahDikirim = new java.util.HashSet<>();
            List<String> links = new java.util.ArrayList<>();

            for (com.mudik.model.PendaftaranMudik p : penumpang) {
                String hp = (p.no_hp_peserta != null && p.no_hp_peserta.length() > 7)
                        ? p.no_hp_peserta : (p.user != null ? p.user.no_hp : null);
                if (hp != null && !hpSudahDikirim.contains(hp)) {
                    String link = waService.generateKuotaPenuhLink(hp, p.nama_peserta, rute.tujuan);
                    links.add(link);
                    hpSudahDikirim.add(hp);
                }
            }

            return Response.ok(Map.of(
                    "status", "BERHASIL",
                    "pesan", "Siap mengirim notifikasi ke " + links.size() + " nomor unik",
                    "wa_links", links
            )).build();
        } catch (Exception e) {
            return Response.status(400).entity(Map.of("error", e.getMessage())).build();
        }
    }
}