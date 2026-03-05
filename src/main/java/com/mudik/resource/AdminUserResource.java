package com.mudik.resource;

import com.mudik.model.User;
import io.quarkus.elytron.security.common.BcryptUtil;
import io.quarkus.panache.common.Sort;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Path("/api/admin/users")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class AdminUserResource {

    // ============================================================
    // Helper: konversi User → Map untuk response JSON
    // ============================================================
    private Map<String, Object> userToMap(User u) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", u.user_id);
        map.put("user_id", u.user_id);
        map.put("nama_lengkap", u.nama_lengkap != null ? u.nama_lengkap : "");
        map.put("email", u.email != null ? u.email : "");
        map.put("nik", u.nik != null ? u.nik : "");
        map.put("no_hp", u.no_hp != null ? u.no_hp : "");
        map.put("role", u.role != null ? u.role : "USER");
        map.put("status_akun", u.status_akun != null ? u.status_akun : "BELUM_VERIF");
        map.put("created_at", u.created_at != null ? u.created_at.toString() : "-");
        return map;
    }

    // ============================================================
    // 1. GET ALL USERS
    // ============================================================
    @GET
    public Response getAllUsers() {
        List<User> users = User.listAll(Sort.descending("user_id"));
        List<Map<String, Object>> result = users.stream()
                .map(this::userToMap)
                .collect(Collectors.toList());
        return Response.ok(result).build();
    }

    // ============================================================
    // 1b. SEARCH USERS — digunakan oleh fitur kaitkan akun Go Show
    //     GET /api/admin/users/search?q=...&limit=20
    //     Pencarian berdasarkan: nama_lengkap, email, no_hp, nik
    //     Hanya mengembalikan user dengan role USER (bukan ADMIN)
    // ============================================================
    @GET
    @Path("/search")
    public Response searchUsers(
            @QueryParam("q") @DefaultValue("") String query,
            @QueryParam("limit") @DefaultValue("20") int limit) {
        try {
            List<User> hasil;

            if (query == null || query.trim().isEmpty()) {
                // Tanpa query: kembalikan user terbaru
                hasil = User.list("role != 'ADMIN' ORDER BY user_id DESC");
            } else {
                String q = query.trim().toLowerCase();
                // Cari di nama, email, no_hp, nik (case-insensitive)
                hasil = User.list(
                        "role != 'ADMIN' AND (" +
                                "  LOWER(nama_lengkap) LIKE ?1 OR " +
                                "  LOWER(email) LIKE ?1 OR " +
                                "  no_hp LIKE ?2 OR " +
                                "  nik LIKE ?2" +
                                ") ORDER BY nama_lengkap ASC",
                        "%" + q + "%",
                        "%" + query.trim() + "%"
                );
            }

            // Batasi jumlah hasil
            int batas = Math.min(limit, 50);
            List<Map<String, Object>> result = hasil.stream()
                    .limit(batas)
                    .map(this::userToMap)
                    .collect(Collectors.toList());

            return Response.ok(result).build();
        } catch (Exception e) {
            e.printStackTrace();
            return Response.status(500).entity(Map.of("error", e.getMessage())).build();
        }
    }

    // ============================================================
    // 2. STATS USER
    // ============================================================
    @GET
    @Path("/stats")
    public Response getUserStats() {
        Map<String, Long> stats = new HashMap<>();
        try {
            stats.put("total_user", User.count());
            stats.put("user_aktif", User.count("status_akun", "AKTIF"));
            stats.put("user_belum_verif", User.count("status_akun", "BELUM_VERIF"));
        } catch (Exception e) {
            stats.put("error", 0L);
        }
        return Response.ok(stats).build();
    }

    // ============================================================
    // 3. UPDATE STATUS (per user)
    // ============================================================
    @PUT
    @Path("/{id}/status")
    @Transactional
    public Response updateStatus(@PathParam("id") Long id, Map<String, String> body) {
        User user = User.findById(id);
        if (user == null) return Response.status(404).entity(Map.of("error", "User tidak ditemukan")).build();

        String statusBaru = body.get("status");
        if (statusBaru != null) {
            user.status_akun = statusBaru;
            if ("AKTIF".equalsIgnoreCase(user.status_akun)) user.verification_token = null;
            user.persist();
        }
        return Response.ok(Map.of("message", "Status berhasil diupdate")).build();
    }

    // ============================================================
    // 4. RESET PASSWORD (per user)
    // ============================================================
    @PUT
    @Path("/{id}/reset-password")
    @Transactional
    public Response resetPasswordUser(@PathParam("id") Long id, Map<String, String> body) {
        User user = User.findById(id);
        if (user == null) return Response.status(404).build();

        String pwd = body.get("password_baru");
        if (pwd != null && !pwd.isEmpty()) {
            user.password_hash = BcryptUtil.bcryptHash(pwd);
            user.persist();
        }
        return Response.ok(Map.of("message", "Password berhasil direset")).build();
    }

    // ============================================================
    // 5. DELETE USER
    // ============================================================
    @DELETE
    @Path("/{id}")
    @Transactional
    public Response deleteUser(@PathParam("id") Long id) {
        User user = User.findById(id);
        if (user == null) return Response.status(404).build();
        if ("ADMIN".equalsIgnoreCase(user.role)) return Response.status(403).build();
        user.delete();
        return Response.ok(Map.of("message", "User dihapus")).build();
    }

    // ============================================================
    // 6. VERIF CEPAT — Verifikasi SEMUA user BELUM_VERIF sekaligus
    //    Gunakan saat situasi darurat / batch approval
    // ============================================================
    @PUT
    @Path("/verif-semua")
    @Transactional
    public Response verifSemua() {
        try {
            List<User> belumVerif = User.list("status_akun", "BELUM_VERIF");
            int jumlah = 0;
            for (User u : belumVerif) {
                u.status_akun = "AKTIF";
                u.verification_token = null;
                u.persist();
                jumlah++;
            }
            return Response.ok(Map.of(
                    "status", "SUKSES",
                    "jumlah_diverifikasi", jumlah,
                    "pesan", jumlah + " akun berhasil diverifikasi."
            )).build();
        } catch (Exception e) {
            return Response.status(500).entity(Map.of(
                    "error", "Gagal melakukan verifikasi massal: " + e.getMessage()
            )).build();
        }
    }
}