package com.mudik.resource;

import com.mudik.model.User;
import io.quarkus.elytron.security.common.BcryptUtil;
import io.quarkus.panache.common.Sort;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject; // Tambahin ini
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Path("/api/admin/users")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
// 🔥 HAPUS DULU @RolesAllowed BUAT DEBUGGING KALAU MASIH GAK MUNCUL
// @RolesAllowed("ADMIN")
public class AdminUserResource {

    // ==========================================
    // 1. LIHAT SEMUA USER (List Tabel) - DIPERBAIKI
    // ==========================================
    @GET
    public Response getAllUsers() {
        // Pake Response.ok() biar header-nya bener
        // Sort by ID descending (terbaru paling atas)
        List<User> users = User.listAll(Sort.descending("user_id"));
        return Response.ok(users).build();
    }

    // ==========================================
    // 2. MONITORING & STATISTIK (Buat Dashboard)
    // ==========================================
    @GET
    @Path("/stats")
    public Response getUserStats() {
        Map<String, Long> stats = new HashMap<>();
        stats.put("total_user", User.count());
        try {
            stats.put("user_aktif", User.count("status_akun", "AKTIF"));
            stats.put("belum_verif", User.count("status_akun", "BELUM_VERIF"));
            stats.put("user_banned", User.count("status_akun", "BANNED"));
        } catch (Exception e) {
            // Fallback kalau kolom status_akun belum ada/error
            stats.put("user_aktif", 0L);
            stats.put("belum_verif", 0L);
            stats.put("user_banned", 0L);
        }

        return Response.ok(stats).build();
    }

    // ==========================================
    // 3. LIHAT DETAIL SATU USER
    // ==========================================
    @GET
    @Path("/{id}")
    public Response getUserDetail(@PathParam("id") Long id) {
        User user = User.findById(id);
        if (user == null) return Response.status(404).entity(Map.of("error", "User tidak ditemukan")).build();
        return Response.ok(user).build();
    }

    // ==========================================
    // 4. UBAH STATUS (Aktifkan / Banned)
    // ==========================================
    @PUT
    @Path("/{id}/status")
    @Transactional
    public Response updateStatus(@PathParam("id") Long id, Map<String, String> body) {
        User user = User.findById(id);
        if (user == null) return Response.status(404).entity(Map.of("error", "User 404")).build();

        String statusBaru = body.get("status"); // JSON: {"status": "AKTIF"}

        if (statusBaru == null || statusBaru.isEmpty()) {
            return Response.status(400).entity(Map.of("error", "Status harus diisi")).build();
        }

        user.status_akun = statusBaru;

        // Kalau di-aktifkan manual, token verifikasi dihapus biar bersih
        if ("AKTIF".equalsIgnoreCase(statusBaru)) {
            user.verification_token = null;
        }

        user.persist();
        return Response.ok(Map.of("message", "Status user berubah menjadi " + statusBaru)).build();
    }

    // ==========================================
    // 5. RESET PASSWORD USER (Admin Paksa Ganti)
    // ==========================================
    @PUT
    @Path("/{id}/reset-password")
    @Transactional
    public Response resetPasswordUser(@PathParam("id") Long id, Map<String, String> body) {
        User user = User.findById(id);
        if (user == null) return Response.status(404).build();

        String passwordBaru = body.get("password_baru");

        if (passwordBaru == null || passwordBaru.length() < 6) {
            return Response.status(400).entity(Map.of("error", "Password minimal 6 karakter")).build();
        }

        // Hash Password Baru
        user.password_hash = BcryptUtil.bcryptHash(passwordBaru);
        user.persist();

        return Response.ok(Map.of("message", "Password berhasil direset.")).build();
    }

    // ==========================================
    // 6. HAPUS USER PERMANEN
    // ==========================================
    @DELETE
    @Path("/{id}")
    @Transactional
    public Response deleteUser(@PathParam("id") Long id) {
        User user = User.findById(id);
        if (user == null) return Response.status(404).build();

        // Mencegah admin menghapus dirinya sendiri
        // Cek Role (Null Safe)
        if (user.role != null && "ADMIN".equals(user.role)) {
            return Response.status(403).entity(Map.of("error", "Tidak bisa menghapus Admin.")).build();
        }

        user.delete();
        return Response.ok(Map.of("message", "User berhasil dihapus permanen")).build();
    }
}