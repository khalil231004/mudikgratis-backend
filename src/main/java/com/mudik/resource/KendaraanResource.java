package com.mudik.resource;

import com.mudik.model.Kendaraan;
import com.mudik.model.Rute;
import jakarta.persistence.EntityManager;
import jakarta.inject.Inject;
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

    @Inject
    EntityManager em;

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
        return Response.ok(Map.of("status", "BERHASIL", "message", "Bus " + kendaraan.nama_armada + " ditambahkan.")).build();
    }

    @GET
    public Response getKendaraan(@QueryParam("rute_id") Long ruteId) {
        List<Kendaraan> list = (ruteId != null)
                ? Kendaraan.list("rute.rute_id", ruteId)
                : Kendaraan.listAll();
        List<Map<String, Object>> result = list.stream().map(this::toResponseMap).collect(Collectors.toList());
        return Response.ok(result).build();
    }

    @DELETE
    @Path("/{id}")
    @Transactional
    public Response hapusKendaraan(@PathParam("id") Long id) {
        Kendaraan k = Kendaraan.findById(id);
        if (k == null) return Response.status(404).entity(Map.of("error", "Bus tidak ditemukan")).build();
        k.delete();
        return Response.ok(Map.of("status", "BERHASIL", "message", "Bus dihapus.")).build();
    }

    @PUT
    @Path("/{id}")
    @Transactional
    public Response editKendaraan(@PathParam("id") Long id, @QueryParam("rute_id") Long ruteId, Kendaraan body) {

        // Validasi rute dulu sebelum apapun
        if (ruteId != null) {
            long ruteCount = (long) em.createNativeQuery("SELECT COUNT(*) FROM rute WHERE rute_id = ?1")
                    .setParameter(1, ruteId)
                    .getSingleResult();
            if (ruteCount == 0)
                return Response.status(404).entity(Map.of("error", "Rute tidak ditemukan")).build();
        }

        // Update field biasa
        em.createNativeQuery(
                        "UPDATE kendaraan SET " +
                                "nama_armada = COALESCE(?1, nama_armada), " +
                                "jenis_kendaraan = COALESCE(?2, jenis_kendaraan), " +
                                "plat_nomor = COALESCE(?3, plat_nomor), " +
                                "kapasitas_total = CASE WHEN ?4 > 0 THEN ?4 ELSE kapasitas_total END, " +
                                "nama_supir = COALESCE(?5, nama_supir), " +
                                "kontak_supir = COALESCE(?6, kontak_supir), " +
                                "rute_id = COALESCE(?7, rute_id) " +
                                "WHERE id = ?8")
                .setParameter(1, body.nama_armada)
                .setParameter(2, body.jenis_kendaraan)
                .setParameter(3, body.plat_nomor)
                .setParameter(4, body.kapasitas_total)
                .setParameter(5, body.nama_supir)
                .setParameter(6, body.kontak_supir)
                .setParameter(7, ruteId)   // null = tidak ganti rute, isi = ganti
                .setParameter(8, id)
                .executeUpdate();

        // Clear L1 cache lalu baca ulang dari DB
        em.flush();
        em.clear();

        // Baca hasil akhir langsung dari DB via native query (100% fresh)
        List<Object[]> rows = em.createNativeQuery(
                        "SELECT k.id, k.nama_armada, k.jenis_kendaraan, k.plat_nomor, " +
                                "k.kapasitas_total, k.terisi, k.nama_supir, k.kontak_supir, " +
                                "k.rute_id, r.asal, r.tujuan, r.tanggal_keberangkatan " +
                                "FROM kendaraan k LEFT JOIN rute r ON r.rute_id = k.rute_id " +
                                "WHERE k.id = ?1")
                .setParameter(1, id)
                .getResultList();

        if (rows.isEmpty()) return Response.status(404).entity(Map.of("error", "Bus tidak ditemukan")).build();

        Object[] row = rows.get(0);
        Map<String, Object> result = new HashMap<>();
        result.put("id",              row[0]);
        result.put("nama_armada",     row[1]);
        result.put("jenis_kendaraan", row[2]);
        result.put("plat_nomor",      row[3]);
        result.put("kapasitas_total", row[4]);
        result.put("terisi",          row[5]);
        result.put("nama_supir",      row[6]);
        result.put("kontak_supir",    row[7]);
        result.put("rute_id",         row[8]);
        result.put("rute_asal",       row[9]);
        result.put("rute_tujuan",     row[10]);
        result.put("waktu_berangkat", row[11]);

        System.out.println("[EDIT BUS OK] id=" + row[0] + " rute_id=" + row[8] + " rute=" + row[10]);

        return Response.ok(result).build();
    }
}