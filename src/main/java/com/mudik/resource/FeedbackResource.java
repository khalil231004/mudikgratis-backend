package com.mudik.resource;

import com.mudik.model.Feedback;
import com.mudik.model.User;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * FeedbackResource — endpoint untuk fitur rating & komentar kepuasan user.
 *
 * PUBLIC:
 *   GET  /api/feedback/publik       → ambil feedback yang sudah disetujui (untuk Home)
 *   GET  /api/feedback/stats        → rata-rata rating & jumlah ulasan
 *   POST /api/feedback              → kirim feedback baru (perlu userId header)
 *
 * ADMIN:
 *   GET  /api/feedback/admin        → semua feedback (termasuk belum disetujui)
 *   PUT  /api/feedback/{id}/setujui → setujui/batalkan tampil di homepage
 *   DELETE /api/feedback/{id}       → hapus feedback
 */
@Path("/api/feedback")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class FeedbackResource {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("dd MMM yyyy");

    // ================================================================
    // PUBLIC
    // ================================================================

    /** GET /api/feedback/publik — untuk section review di Home.tsx */
    @GET
    @Path("/publik")
    public Response getFeedbackPublik(@QueryParam("limit") @DefaultValue("10") int limit) {
        // FIX: gunakan JPQL eksplisit agar boolean comparison benar di semua DB
        List<Feedback> list = Feedback.find(
                "FROM Feedback f WHERE f.disetujui = true ORDER BY f.dikirim_at DESC"
        ).list();

        List<Map<String, Object>> result = toMapList(list.stream()
                .limit(limit)
                .collect(Collectors.toList()));

        return Response.ok(result).build();
    }

    /** GET /api/feedback/publik/bintang5 — khusus komentar bintang 5 (disetujui admin) */
    @GET
    @Path("/publik/bintang5")
    public Response getFeedbackBintang5(@QueryParam("limit") @DefaultValue("6") int limit) {
        List<Feedback> list = Feedback.find(
                "FROM Feedback f WHERE f.disetujui = true AND f.rating = 5 ORDER BY f.dikirim_at DESC"
        ).list();

        List<Map<String, Object>> result = toMapList(list.stream()
                .limit(limit)
                .collect(Collectors.toList()));

        return Response.ok(result).build();
    }

    /** GET /api/feedback/stats — rata-rata rating untuk counter di Home */
    @GET
    @Path("/stats")
    public Response getStats() {
        long total = Feedback.count("disetujui = true");
        Double avg = (Double) Feedback.getEntityManager()
                .createQuery("SELECT AVG(f.rating) FROM Feedback f WHERE f.disetujui = true")
                .getSingleResult();

        return Response.ok(Map.of(
                "total_ulasan", total,
                "rata_rata", avg != null ? Math.round(avg * 10.0) / 10.0 : 0.0
        )).build();
    }

    /** POST /api/feedback — kirim feedback baru dari user */
    @POST
    @Transactional
    public Response kirimFeedback(
            @HeaderParam("userId") String userIdStr,
            Map<String, Object> body
    ) {
        try {
            if (!body.containsKey("rating")) {
                return Response.status(400).entity(Map.of("error", "Rating wajib diisi")).build();
            }

            int rating;
            try {
                rating = Integer.parseInt(body.get("rating").toString());
            } catch (NumberFormatException e) {
                return Response.status(400).entity(Map.of("error", "Rating harus angka 1–5")).build();
            }

            if (rating < 1 || rating > 5) {
                return Response.status(400).entity(Map.of("error", "Rating harus antara 1 dan 5")).build();
            }

            Feedback fb = new Feedback();
            fb.rating = rating;
            fb.komentar = body.containsKey("komentar") ? body.get("komentar").toString() : null;

            // Coba ambil nama dari user jika ada
            if (userIdStr != null && !userIdStr.isBlank() && !"undefined".equals(userIdStr)) {
                try {
                    String cleaned = userIdStr.split(",")[0].trim().replace("\"", "");
                    Long userId = Long.parseLong(cleaned);
                    User user = User.findById(userId);
                    if (user != null) {
                        fb.user = user;
                        fb.nama_pengirim = body.containsKey("nama_pengirim")
                                ? body.get("nama_pengirim").toString()
                                : user.nama_lengkap;
                    }
                } catch (NumberFormatException ignored) {}
            }

            if (fb.nama_pengirim == null || fb.nama_pengirim.isBlank()) {
                fb.nama_pengirim = body.containsKey("nama_pengirim")
                        ? body.get("nama_pengirim").toString()
                        : "Anonim";
            }

            fb.disetujui = false; // Default belum disetujui, admin harus approve
            fb.persist();

            return Response.ok(Map.of(
                    "status", "BERHASIL",
                    "pesan", "Terima kasih atas feedback Anda! Akan ditampilkan setelah disetujui admin."
            )).build();

        } catch (Exception e) {
            return Response.status(500).entity(Map.of("error", e.getMessage())).build();
        }
    }

    // ================================================================
    // ADMIN
    // ================================================================

    /**
     * GET /api/feedback/list-admin — semua feedback untuk admin panel
     * Path pakai /list-admin agar tidak konflik dengan @DELETE /{id} di JAX-RS routing.
     */
    @GET
    @Path("/list-admin")
    public Response getAllAdmin() {
        List<Feedback> list = Feedback.find("FROM Feedback f ORDER BY f.dikirim_at DESC").list();
        return Response.ok(toMapList(list)).build();
    }

    /**
     * GET /api/feedback/admin — alias untuk /list-admin (backward compatibility).
     * Dibuat terpisah karena JAX-RS tidak bisa punya dua @Path("/admin") dan @Path("/{id}") di kelas yang sama
     * tanpa explicit @GET — endpoint ini mencegah 405 Method Not Allowed dari cache browser / request lama.
     */
    @GET
    @Path("/admin")
    public Response getAllAdminAlias() {
        return getAllAdmin();
    }

    /** PUT /api/feedback/{id}/setujui — toggle setujui/batalkan */
    @PUT
    @Path("/{id}/setujui")
    @Transactional
    public Response setujuiFeedback(@PathParam("id") Long id, Map<String, Object> body) {
        Feedback fb = Feedback.findById(id);
        if (fb == null) return Response.status(404).entity(Map.of("error", "Feedback tidak ditemukan")).build();

        boolean setujui = body.containsKey("disetujui")
                && Boolean.parseBoolean(body.get("disetujui").toString());
        fb.disetujui = setujui;
        fb.persist();

        return Response.ok(Map.of(
                "status", "BERHASIL",
                "disetujui", fb.disetujui,
                "pesan", setujui ? "Feedback disetujui dan akan tampil di halaman utama" : "Persetujuan dibatalkan"
        )).build();
    }

    /** DELETE /api/feedback/{id} — hapus feedback */
    @DELETE
    @Path("/{id}")
    @Transactional
    public Response hapusFeedback(@PathParam("id") Long id) {
        Feedback fb = Feedback.findById(id);
        if (fb == null) return Response.status(404).entity(Map.of("error", "Feedback tidak ditemukan")).build();

        fb.delete();
        return Response.ok(Map.of("status", "BERHASIL", "pesan", "Feedback dihapus")).build();
    }

    // ================================================================
    // HELPER
    // ================================================================
    private List<Map<String, Object>> toMapList(List<Feedback> list) {
        return list.stream().map(fb -> {
            Map<String, Object> m = new HashMap<>();
            m.put("id", fb.id);
            m.put("rating", fb.rating);
            m.put("komentar", fb.komentar != null ? fb.komentar : "");
            m.put("nama_pengirim", fb.nama_pengirim != null ? fb.nama_pengirim : "Anonim");
            m.put("dikirim_at", fb.dikirim_at != null ? fb.dikirim_at.format(FMT) : "-");
            m.put("disetujui", fb.disetujui);
            return m;
        }).collect(Collectors.toList());
    }
}
