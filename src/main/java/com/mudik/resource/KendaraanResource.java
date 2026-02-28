package com.mudik.resource;

import com.mudik.model.Kendaraan;
import com.mudik.model.Rute;
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
        // ── STEP 1: Update field biasa dulu via JPQL ──────────────────
        // Bangun query dinamis hanya untuk field yang dikirim
        StringBuilder jpql = new StringBuilder("UPDATE Kendaraan k SET k.id = k.id");

        if (body.nama_armada != null)    jpql.append(", k.nama_armada = :nama");
        if (body.jenis_kendaraan != null) jpql.append(", k.jenis_kendaraan = :jenis");
        if (body.plat_nomor != null)     jpql.append(", k.plat_nomor = :plat");
        if (body.kapasitas_total > 0)    jpql.append(", k.kapasitas_total = :kap");
        if (body.nama_supir != null)     jpql.append(", k.nama_supir = :supir");
        if (body.kontak_supir != null)   jpql.append(", k.kontak_supir = :kontak");

        // ── Update rute via FK langsung di JPQL ───────────────────────
        Rute ruteObj = null;
        if (ruteId != null) {
            ruteObj = Rute.findById(ruteId);
            if (ruteObj == null)
                return Response.status(404).entity(Map.of("error", "Rute tidak ditemukan")).build();
            jpql.append(", k.rute = :rute");
        }

        jpql.append(" WHERE k.id = :id");

        var query = Kendaraan.getEntityManager().createQuery(jpql.toString());

        if (body.nama_armada != null)    query.setParameter("nama", body.nama_armada);
        if (body.jenis_kendaraan != null) query.setParameter("jenis", body.jenis_kendaraan);
        if (body.plat_nomor != null)     query.setParameter("plat", body.plat_nomor);
        if (body.kapasitas_total > 0)    query.setParameter("kap", body.kapasitas_total);
        if (body.nama_supir != null)     query.setParameter("supir", body.nama_supir);
        if (body.kontak_supir != null)   query.setParameter("kontak", body.kontak_supir);
        if (ruteObj != null)             query.setParameter("rute", ruteObj);

        query.setParameter("id", id);
        query.executeUpdate();

        // ── STEP 2: Flush + load ulang entity dari DB ─────────────────
        Kendaraan.getEntityManager().flush();
        Kendaraan.getEntityManager().clear(); // clear cache L1 agar findById baca dari DB

        Kendaraan hasil = Kendaraan.findById(id);
        if (hasil == null) return Response.status(404).entity(Map.of("error", "Bus tidak ditemukan setelah update")).build();

        System.out.println("[EDIT BUS] ID=" + id + " rute_id sekarang=" + (hasil.rute != null ? hasil.rute.rute_id : "null"));

        return Response.ok(toResponseMap(hasil)).build();
    }
}