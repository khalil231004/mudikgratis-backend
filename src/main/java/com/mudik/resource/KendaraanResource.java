package com.mudik.resource;
import com.mudik.model.Kendaraan;
import com.mudik.model.Rute;
import jakarta.persistence.LockModeType;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.stream.Collectors;

@Path("/api/admin/kendaraan")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class KendaraanResource {

    // Helper: flatten Kendaraan + Rute jadi satu Map untuk response
    private Map<String, Object> toResponseMap(Kendaraan k) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", k.id);
        map.put("nama_armada", k.nama_armada);
        map.put("jenis_kendaraan", k.jenis_kendaraan);
        map.put("plat_nomor", k.plat_nomor);
        map.put("kapasitas_total", k.kapasitas_total);
        map.put("terisi", k.terisi);
        map.put("nama_supir", k.nama_supir);
        map.put("kontak_supir", k.kontak_supir);

        // Flatten data rute supaya frontend bisa langsung baca
        if (k.rute != null) {
            map.put("rute_id", k.rute.rute_id);
            map.put("rute_asal", k.rute.asal);
            map.put("rute_tujuan", k.rute.tujuan);
            map.put("waktu_berangkat", k.rute.tanggal_keberangkatan);
        } else {
            map.put("rute_id", null);
            map.put("rute_asal", null);
            map.put("rute_tujuan", null);
            map.put("waktu_berangkat", null);
        }

        return map;
    }

    // --- 1. TAMBAH BUS BARU ---
    @POST
    @Transactional
    public Response tambahKendaraan(@QueryParam("rute_id") Long ruteId, Kendaraan kendaraan) {
        Rute rute = Rute.findById(ruteId);
        if (rute == null) return Response.status(404).entity(Map.of("error", "Rute tidak ditemukan")).build();
        if (kendaraan.nama_armada == null || kendaraan.kapasitas_total <= 0) {
            return Response.status(400).entity(Map.of("error", "Nama armada & kapasitas wajib diisi")).build();
        }
        kendaraan.rute = rute;
        kendaraan.terisi = 0;
        kendaraan.persist();
        return Response.ok(Map.of(
                "status", "BERHASIL",
                "message", "Bus " + kendaraan.nama_armada + " ditambahkan. (Kuota Rute TIDAK BERUBAH)"
        )).build();
    }

    // --- 2. LIHAT DAFTAR BUS (dengan data rute di-flatten) ---
    @GET
    public Response getKendaraan(@QueryParam("rute_id") Long ruteId) {
        List<Kendaraan> list = (ruteId != null)
                ? Kendaraan.list("rute.rute_id", ruteId)
                : Kendaraan.listAll();

        List<Map<String, Object>> result = list.stream()
                .map(this::toResponseMap)
                .collect(Collectors.toList());

        return Response.ok(result).build();
    }

    // --- 3. HAPUS BUS ---
    @DELETE
    @Path("/{id}")
    @Transactional
    public Response hapusKendaraan(@PathParam("id") Long id) {
        Kendaraan k = Kendaraan.findById(id);
        if (k == null) return Response.status(404).entity(Map.of("error", "Bus tidak ditemukan")).build();
        k.delete();
        return Response.ok(Map.of("status", "BERHASIL", "message", "Bus dihapus. Kuota Rute Tetap.")).build();
    }

    // --- 4. EDIT BUS ---
    @PUT
    @Path("/{id}")
    @Transactional
    public Response editKendaraan(@PathParam("id") Long id, @QueryParam("rute_id") Long ruteId, Kendaraan body) {
        Kendaraan k = Kendaraan.findById(id);
        if (k == null) return Response.status(404).entity(Map.of("error", "Bus tidak ditemukan")).build();

        if (body.nama_armada != null) k.nama_armada = body.nama_armada;
        if (body.jenis_kendaraan != null) k.jenis_kendaraan = body.jenis_kendaraan;
        if (body.plat_nomor != null) k.plat_nomor = body.plat_nomor;
        if (body.kapasitas_total > 0) k.kapasitas_total = body.kapasitas_total;
        if (body.nama_supir != null) k.nama_supir = body.nama_supir;
        if (body.kontak_supir != null) k.kontak_supir = body.kontak_supir;

        if (ruteId != null) {
            Rute rute = Rute.findById(ruteId);
            if (rute == null) return Response.status(404).entity(Map.of("error", "Rute tidak ditemukan")).build();
            k.rute = rute;
        }

        return Response.ok(toResponseMap(k)).build();
    }
}