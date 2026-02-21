package com.mudik.resource;

import com.mudik.model.Terminal;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * TerminalResource — CRUD data terminal / titik pemberhentian bus.
 *
 * Koordinat dipakai oleh PetaResource untuk menampilkan sebaran pemudik.
 * Semua rute butuh terminal tujuan yang terdefinisi agar muncul di peta.
 *
 * PUBLIC:
 *   GET  /api/terminal           → daftar semua terminal
 *   GET  /api/terminal/{id}      → detail satu terminal
 *
 * ADMIN:
 *   POST   /api/terminal         → tambah terminal baru
 *   PUT    /api/terminal/{id}    → edit terminal
 *   DELETE /api/terminal/{id}    → hapus terminal
 *
 * DATA TERMINAL ACEH & TUJUAN MUDIK UMUM (seed manual):
 *   Terminal Batoh, Banda Aceh   → lat: 5.5299, lon: 95.3288 (asal default)
 *   Terminal Lhokseumawe         → lat: 5.1801, lon: 97.1419
 *   Terminal Langsa              → lat: 4.4683, lon: 97.9620
 *   Terminal Medan (Amplas)      → lat: 3.5397, lon: 98.6880
 *   Terminal Medan (Pinang Baris)→ lat: 3.6244, lon: 98.6179
 *   Terminal Padang Sidempuan    → lat: 1.3985, lon: 99.2717
 *   Terminal Pekanbaru           → lat: 0.5071, lon: 101.4478
 *   Terminal Padang              → lat: -0.9571, lon: 100.4219
 *   Terminal Jakarta (Pulo Gadung)→ lat: -6.1941, lon: 106.9053
 *   Terminal Surabaya (Bungurasih)→ lat: -7.3737, lon: 112.7300
 */
@Path("/api/terminal")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class TerminalResource {

    // ================================================================
    // PUBLIC
    // ================================================================

    /** GET /api/terminal — semua terminal, dipakai PetaResource & admin */
    @GET
    public Response getAll() {
        List<Terminal> list = Terminal.listAll();
        List<Map<String, Object>> result = list.stream().map(this::toMap).collect(Collectors.toList());
        return Response.ok(result).build();
    }

    /** GET /api/terminal/{id} — detail satu terminal */
    @GET
    @Path("/{id}")
    public Response getOne(@PathParam("id") Long id) {
        Terminal t = Terminal.findById(id);
        if (t == null) return Response.status(404).entity(Map.of("error", "Terminal tidak ditemukan")).build();
        return Response.ok(toMap(t)).build();
    }

    // ================================================================
    // ADMIN — CRUD
    // ================================================================

    /**
     * POST /api/terminal
     * Body: { "nama": "Terminal Batoh", "kota": "Banda Aceh", "latitude": 5.5299, "longitude": 95.3288 }
     */
    @POST
    @Transactional
    public Response tambah(Map<String, Object> body) {
        try {
            if (!body.containsKey("nama") || !body.containsKey("kota")) {
                return Response.status(400).entity(Map.of("error", "Nama dan kota wajib diisi")).build();
            }
            if (!body.containsKey("latitude") || !body.containsKey("longitude")) {
                return Response.status(400).entity(Map.of("error", "Koordinat latitude & longitude wajib diisi")).build();
            }

            Terminal t = new Terminal();
            t.nama      = body.get("nama").toString();
            t.kota      = body.get("kota").toString();
            t.latitude  = Double.parseDouble(body.get("latitude").toString());
            t.longitude = Double.parseDouble(body.get("longitude").toString());
            t.persist();

            return Response.ok(Map.of(
                "status", "BERHASIL",
                "pesan", "Terminal '" + t.nama + "' berhasil ditambahkan",
                "id", t.id,
                "data", toMap(t)
            )).build();

        } catch (NumberFormatException e) {
            return Response.status(400).entity(Map.of("error", "Koordinat harus berupa angka desimal (contoh: 5.5299)")).build();
        } catch (Exception e) {
            return Response.status(500).entity(Map.of("error", e.getMessage())).build();
        }
    }

    /**
     * PUT /api/terminal/{id}
     * Body: { "nama": "...", "kota": "...", "latitude": ..., "longitude": ... }
     */
    @PUT
    @Path("/{id}")
    @Transactional
    public Response update(@PathParam("id") Long id, Map<String, Object> body) {
        Terminal t = Terminal.findById(id);
        if (t == null) return Response.status(404).entity(Map.of("error", "Terminal tidak ditemukan")).build();

        try {
            if (body.containsKey("nama"))      t.nama      = body.get("nama").toString();
            if (body.containsKey("kota"))      t.kota      = body.get("kota").toString();
            if (body.containsKey("latitude"))  t.latitude  = Double.parseDouble(body.get("latitude").toString());
            if (body.containsKey("longitude")) t.longitude = Double.parseDouble(body.get("longitude").toString());
            t.persist();

            return Response.ok(Map.of(
                "status", "BERHASIL",
                "pesan", "Terminal berhasil diperbarui",
                "data", toMap(t)
            )).build();

        } catch (NumberFormatException e) {
            return Response.status(400).entity(Map.of("error", "Koordinat harus berupa angka desimal")).build();
        }
    }

    /** DELETE /api/terminal/{id} */
    @DELETE
    @Path("/{id}")
    @Transactional
    public Response hapus(@PathParam("id") Long id) {
        Terminal t = Terminal.findById(id);
        if (t == null) return Response.status(404).entity(Map.of("error", "Terminal tidak ditemukan")).build();
        String nama = t.nama;
        t.delete();
        return Response.ok(Map.of("status", "BERHASIL", "pesan", "Terminal '" + nama + "' dihapus")).build();
    }

    // ================================================================
    // HELPER
    // ================================================================
    private Map<String, Object> toMap(Terminal t) {
        Map<String, Object> m = new HashMap<>();
        m.put("id",        t.id);
        m.put("nama",      t.nama);
        m.put("kota",      t.kota);
        m.put("latitude",  t.latitude);
        m.put("longitude", t.longitude);
        // Info tampilan untuk frontend maps
        m.put("label",     t.nama + " (" + t.kota + ")");
        m.put("koordinat", t.latitude + ", " + t.longitude);
        return m;
    }
}
