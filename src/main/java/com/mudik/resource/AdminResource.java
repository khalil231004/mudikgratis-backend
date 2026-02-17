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
    // 3. VERIFIKASI INDIVIDUAL (🔥 FIX ERROR 404 & BATAL/RESET)
    // =================================================================
    // Endpoint ini yang sebelumnya HILANG, makanya error 404 saat klik aksi cepat
    @PUT
    @Path("/verifikasi/{id}")
    @Transactional
    public Response updateIndividual(@PathParam("id") Long id, Map<String, String> body) {
        try {
            String statusBaru = body.get("status");
            PendaftaranMudik p = PendaftaranMudik.findById(id);
            if (p == null) return Response.status(404).entity(Map.of("error", "Data tidak ditemukan")).build();

            String statusLama = p.status_pendaftaran;

            // --- LOGIC RESET: DARI DITOLAK/BATAL KE MENUNGGU (AMBIL KUOTA) ---
            if ("MENUNGGU VERIFIKASI".equals(statusBaru) &&
                    ("DITOLAK".equals(statusLama) || "DIBATALKAN".equals(statusLama))) {

                if (p.rute.getSisaKuota() <= 0) {
                    return Response.status(400).entity(Map.of("error", "Kuota Rute Penuh, tidak bisa reset data!")).build();
                }
                // Ambil Kuota
                p.rute.kuota_terisi = (p.rute.kuota_terisi == null ? 0 : p.rute.kuota_terisi) + 1;
                p.alasan_tolak = null;
            }

            // --- LOGIC BATAL/TOLAK: DARI AKTIF KE BATAL (BALIKIN KUOTA) ---
            else if (("DIBATALKAN".equals(statusBaru) || "DITOLAK".equals(statusBaru)) &&
                    (!"DITOLAK".equals(statusLama) && !"DIBATALKAN".equals(statusLama))) {

                // Balikin Kuota (Kecuali Bayi)
                if (!"BAYI".equalsIgnoreCase(p.kategori_penumpang) && p.rute.kuota_terisi > 0) {
                    p.rute.kuota_terisi -= 1;
                }
            }

            // Update Status
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
    // 5. STATISTIK
    // =================================================================
    @GET
    @Path("/stats")
    public Response getDashboardStats() {
        long totalPendaftar = PendaftaranMudik.count();
        long totalDiterima = PendaftaranMudik.count("status_pendaftaran = 'DITERIMA H-3'");
        long totalSiap = PendaftaranMudik.count("status_pendaftaran = 'TERVERIFIKASI/ SIAP BERANGKAT'");
        long totalDitolak = PendaftaranMudik.count("status_pendaftaran = 'DITOLAK'");

        List<Rute> rutes = Rute.listAll();
        long sisaKuotaGlobal = 0;
        for(Rute r : rutes) sisaKuotaGlobal += r.getSisaKuota();

        List<Map<String, Object>> statsRute = rutes.stream().map(rute -> {
            Map<String, Object> map = new HashMap<>();
            map.put("tujuan", rute.tujuan);
            map.put("sisa_kuota", rute.getSisaKuota());
            map.put("terisi", (rute.kuota_terisi != null ? rute.kuota_terisi : 0));
            map.put("total_kursi", rute.kuota_total);
            return map;
        }).collect(Collectors.toList());

        return Response.ok(Map.of(
                "total_masuk", totalPendaftar,
                "total_diterima", totalDiterima,
                "total_siap", totalSiap,
                "total_ditolak", totalDitolak,
                "sisa_kuota_global", sisaKuotaGlobal,
                "detail_rute", statsRute
        )).build();
    }

    // =================================================================
    // 6. EXPORT EXCEL nya
    // =================================================================
    @GET
    @Path("/export")
    @Produces("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    public Response exportExcel(@QueryParam("rute_id") Long ruteId) {
        try {
            List<PendaftaranMudik> list;
            if (ruteId != null) {
                list = PendaftaranMudik.list("rute.rute_id = ?1 ORDER BY nama_peserta ASC", ruteId);
            } else {
                list = PendaftaranMudik.list("ORDER BY rute.tujuan ASC, nama_peserta ASC");
            }
            return Response.ok(excelService.generateLaporanExcel(list))
                    .header("Content-Disposition", "attachment; filename=\"Rekap_Mudik.xlsx\"").build();
        } catch (IOException e) {
            return Response.serverError().entity(Map.of("error", "Gagal generate Excel")).build();
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
}