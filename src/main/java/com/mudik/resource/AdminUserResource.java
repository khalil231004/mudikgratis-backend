package com.mudik.resource;

import com.mudik.model.User;
import io.quarkus.elytron.security.common.BcryptUtil;
import io.quarkus.panache.common.Sort;
import jakarta.annotation.security.RolesAllowed;
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
@RolesAllowed("ADMIN") // 🔥 HANYA ADMIN YANG BISA AKSES
public class AdminUserResource {

    // ==========================================
    // 1. MONITORING & STATISTIK (Buat Dashboard)
    // ==========================================
    @GET
    @Path("/stats")
    public Response getUserStats() {
        Map<String, Long> stats = new HashMap<>();
        stats.put("total_user", User.count());
        stats.put("user_aktif", User.count("status_akun", "AKTIF"));
        stats.put("belum_verif", User.count("status_akun", "BELUM_VERIF"));
        stats.put("user_banned", User.count("status_akun", "BANNED"));

        return Response.ok(stats).build();
    }

    // ==========================================
    // 2. LIHAT SEMUA USER (List Tabel)
    // ==========================================
    @GET
    public List<User> getAllUsers() {
        // Urutkan dari yang terbaru daftar
        // Password hash otomatis disembunyikan kalau entity User lu pake @JsonIgnore di field password
        return User.listAll(Sort.descending("id"));
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
        if (user == null) return Response.status(404).build();

        String statusBaru = body.get("status"); // Kirim JSON: {"status": "AKTIF"} atau {"status": "BANNED"}

        if (statusBaru == null || statusBaru.isEmpty()) {
            return Response.status(400).entity(Map.of("error", "Status harus diisi")).build();
        }

        user.status_akun = statusBaru;

        // Kalau di-aktifkan manual, token verifikasi dihapus biar bersih
        if ("AKTIF".equalsIgnoreCase(statusBaru)) {
            user.verification_token = null;
        }

        user.persist();
        return Response.ok(Map.of("message", "Status user " + user.email + " berubah menjadi " + statusBaru)).build();
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

        String passwordBaru = body.get("password_baru"); // Kirim JSON: {"password_baru": "123456"}

        if (passwordBaru == null || passwordBaru.length() < 6) {
            return Response.status(400).entity(Map.of("error", "Password minimal 6 karakter")).build();
        }

        // 🔥 HASH PASSWORD (PENTING!)
        user.password_hash = BcryptUtil.bcryptHash(passwordBaru);
        user.persist();

        return Response.ok(Map.of("message", "Password untuk " + user.email + " berhasil direset admin.")).build();
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

        // Mencegah admin menghapus dirinya sendiri (Opsional, tapi aman)
        if ("ADMIN".equals(user.role)) {
            return Response.status(403).entity(Map.of("error", "Tidak bisa menghapus sesama Admin lewat API ini.")).build();
        }

        user.delete();
        return Response.ok(Map.of("message", "User berhasil dihapus permanen")).build();
    }
}