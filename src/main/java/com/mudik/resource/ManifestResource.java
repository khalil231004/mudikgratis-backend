package com.mudik.resource;

import com.mudik.model.Kendaraan;
import com.mudik.model.PendaftaranMudik;
import com.mudik.model.Rute;
import com.mudik.service.ExcelService;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * FIX 4 & 5: Manifest per Bus, per Rute, dan Export
 */
@Path("/api/manifest")
@Produces(MediaType.APPLICATION_JSON)
public class ManifestResource {

    @Inject
    ExcelService excelService;

    // ==========================================
    // 1. GET MANIFEST PER RUTE
    // ==========================================
    @GET
    @Path("/rute/{rute_id}")
    public Response manifestPerRute(@PathParam("rute_id") Long ruteId) {
        Rute rute = Rute.findById(ruteId);
        if (rute == null) return Response.status(404).entity(Map.of("error", "Rute tidak ditemukan")).build();

        List<PendaftaranMudik> list = PendaftaranMudik.list(
                "rute.rute_id = ?1 ORDER BY nama_peserta ASC", ruteId);

        Map<String, Object> result = buildManifestData(rute, null, list);
        return Response.ok(result).build();
    }

    // ==========================================
    // 2. GET MANIFEST PER BUS
    // ==========================================
    @GET
    @Path("/bus/{kendaraan_id}")
    public Response manifestPerBus(@PathParam("kendaraan_id") Long kendaraanId) {
        Kendaraan bus = Kendaraan.findById(kendaraanId);
        if (bus == null) return Response.status(404).entity(Map.of("error", "Bus tidak ditemukan")).build();

        // FIX 5: Hanya tampilkan yang TERVERIFIKASI/ SIAP BERANGKAT
        List<PendaftaranMudik> list = PendaftaranMudik.list(
                "kendaraan.id = ?1 AND status_pendaftaran = 'TERVERIFIKASI/ SIAP BERANGKAT' ORDER BY nama_peserta ASC", kendaraanId);

        Map<String, Object> result = buildManifestData(bus.rute, bus, list);
        return Response.ok(result).build();
    }

    // ==========================================
    // 3. GET SEMUA MANIFEST (GROUP BY RUTE & BUS)
    // ==========================================
    @GET
    public Response allManifest() {
        List<Rute> rutes = Rute.listAll();
        List<Map<String, Object>> hasilRute = new ArrayList<>();

        for (Rute rute : rutes) {
            List<PendaftaranMudik> allPenumpang = PendaftaranMudik.list(
                    "rute.rute_id = ?1", rute.rute_id);

            // Group by bus
            Map<String, List<PendaftaranMudik>> perBus = allPenumpang.stream()
                    .collect(Collectors.groupingBy(p ->
                            (p.kendaraan != null) ? p.kendaraan.nama_armada : "Belum Plotting"));

            List<Map<String, Object>> busManifest = new ArrayList<>();
            for (Map.Entry<String, List<PendaftaranMudik>> entry : perBus.entrySet()) {
                Map<String, Object> busMap = new HashMap<>();
                busMap.put("nama_bus", entry.getKey());
                busMap.put("jumlah_penumpang", entry.getValue().size());
                busMap.put("dewasa", entry.getValue().stream().filter(p -> "DEWASA".equalsIgnoreCase(p.kategori_penumpang)).count());
                busMap.put("anak", entry.getValue().stream().filter(p -> "ANAK".equalsIgnoreCase(p.kategori_penumpang)).count());
                busMap.put("bayi", entry.getValue().stream().filter(p -> "BAYI".equalsIgnoreCase(p.kategori_penumpang)).count());
                busManifest.add(busMap);
            }

            Map<String, Object> ruteMap = new HashMap<>();
            ruteMap.put("rute_id", rute.rute_id);
            ruteMap.put("tujuan", rute.tujuan);
            ruteMap.put("asal", rute.asal);
            ruteMap.put("waktu_berangkat", rute.getFormattedDate());
            ruteMap.put("total_penumpang", allPenumpang.size());
            ruteMap.put("per_bus", busManifest);
            hasilRute.add(ruteMap);
        }

        return Response.ok(hasilRute).build();
    }

    // ==========================================
    // 4. EXPORT EXCEL MANIFEST PER BUS
    // ==========================================
    @GET
    @Path("/export/bus/{kendaraan_id}")
    @Produces("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    public Response exportManifestBus(@PathParam("kendaraan_id") Long kendaraanId) {
        try {
            Kendaraan bus = Kendaraan.findById(kendaraanId);
            if (bus == null) return Response.status(404).entity("Bus tidak ditemukan").build();

            List<PendaftaranMudik> list = PendaftaranMudik.list(
                    "kendaraan.id = ?1 ORDER BY nama_peserta ASC", kendaraanId);

            String filename = "Manifest_Bus_" + bus.nama_armada.replace(" ", "_") + ".xlsx";
            return Response.ok(excelService.generateManifestBus(bus, list))
                    .header("Content-Disposition", "attachment; filename=\"" + filename + "\"")
                    .build();
        } catch (IOException e) {
            return Response.serverError().entity(Map.of("error", "Gagal export: " + e.getMessage())).build();
        }
    }

    // ==========================================
    // 5. EXPORT EXCEL MANIFEST PER RUTE
    // ==========================================
    @GET
    @Path("/export/rute/{rute_id}")
    @Produces("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    public Response exportManifestRute(@PathParam("rute_id") Long ruteId) {
        try {
            Rute rute = Rute.findById(ruteId);
            if (rute == null) return Response.status(404).entity("Rute tidak ditemukan").build();

            List<PendaftaranMudik> list = PendaftaranMudik.list(
                    "rute.rute_id = ?1 ORDER BY nama_peserta ASC", ruteId);

            String filename = "Manifest_Rute_" + rute.tujuan.replace(" ", "_") + ".xlsx";
            return Response.ok(excelService.generateLaporanExcel(list))
                    .header("Content-Disposition", "attachment; filename=\"" + filename + "\"")
                    .build();
        } catch (IOException e) {
            return Response.serverError().entity(Map.of("error", "Gagal export: " + e.getMessage())).build();
        }
    }

    // ==========================================
    // 6. EXPORT SEMUA MANIFEST (PERTINGGAL)
    // ==========================================
    @GET
    @Path("/export/semua")
    @Produces("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    public Response exportSemua() {
        try {
            List<PendaftaranMudik> list = PendaftaranMudik.list("ORDER BY rute.tujuan ASC, nama_peserta ASC");
            return Response.ok(excelService.generateLaporanExcel(list))
                    .header("Content-Disposition", "attachment; filename=\"Manifest_Semua_Rute.xlsx\"")
                    .build();
        } catch (IOException e) {
            return Response.serverError().entity(Map.of("error", "Gagal export: " + e.getMessage())).build();
        }
    }

    // ==========================================
    // HELPER
    // ==========================================
    private Map<String, Object> buildManifestData(Rute rute, Kendaraan bus, List<PendaftaranMudik> list) {
        Map<String, Object> result = new HashMap<>();

        if (rute != null) {
            result.put("rute_tujuan", rute.tujuan);
            result.put("rute_asal", rute.asal);
            result.put("waktu_berangkat", rute.getFormattedDate());
        }

        if (bus != null) {
            result.put("nama_bus", bus.nama_armada);
            result.put("plat_nomor", bus.plat_nomor);
            result.put("nama_supir", bus.nama_supir);
            result.put("kontak_supir", bus.kontak_supir);
            result.put("kapasitas_total", bus.kapasitas_total);
            result.put("terisi", bus.terisi);
            result.put("sisa_kursi", (bus.kapasitas_total != null ? bus.kapasitas_total : 0) - (bus.terisi != null ? bus.terisi : 0));
        }

        // Summary
        long dewasa = list.stream().filter(p -> "DEWASA".equalsIgnoreCase(p.kategori_penumpang)).count();
        long anak = list.stream().filter(p -> "ANAK".equalsIgnoreCase(p.kategori_penumpang)).count();
        long bayi = list.stream().filter(p -> "BAYI".equalsIgnoreCase(p.kategori_penumpang)).count();

        result.put("total_penumpang", list.size());
        result.put("jumlah_dewasa", dewasa);
        result.put("jumlah_anak", anak);
        result.put("jumlah_bayi", bayi);

        // List penumpang
        List<Map<String, Object>> penumpangList = list.stream().map(p -> {
            Map<String, Object> pm = new LinkedHashMap<>();
            pm.put("no", list.indexOf(p) + 1);
            pm.put("nama_peserta", p.nama_peserta);
            pm.put("nik", p.nik_peserta);
            pm.put("kategori", p.kategori_penumpang);
            pm.put("jenis_kelamin", p.jenis_kelamin);
            pm.put("alamat", p.alamat_rumah);
            pm.put("no_hp", p.no_hp_peserta != null ? p.no_hp_peserta : (p.user != null ? p.user.no_hp : "-"));
            pm.put("status", p.status_pendaftaran);
            pm.put("kode_booking", p.kode_booking);
            pm.put("nama_bus", p.kendaraan != null ? p.kendaraan.nama_armada : "Belum Plotting");
            return pm;
        }).collect(Collectors.toList());

        result.put("penumpang", penumpangList);
        return result;
    }
}