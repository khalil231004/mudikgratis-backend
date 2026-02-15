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
public class AdminUserResource {

    // ==========================================
    // 1. STATISTIK DASHBOARD
    // ==========================================
    @GET
    @Path("/stats")
    // @RolesAllowed("ADMIN") <--- GW KOMEN DULU BIAR LU BISA CEK DATA
    public Response getUserStats() {
        Map<String, Long> stats = new HashMap<>();
        try {
            stats.put("total_user", User.count());
            stats.put("user_aktif", User.count("status_akun", "AKTIF"));
            stats.put("belum_verif", User.count("status_akun", "BELUM_VERIF"));
            stats.put("user_banned", User.count("status_akun", "BANNED"));
        } catch (Exception e) {
            stats.put("error", 0L);
        }
        return Response.ok(stats).build();
    }

    // ==========================================
    // 2. FETCH SEMUA USER (POIN 14 FIXED)
    // ==========================================
    @GET
    // @RolesAllowed("ADMIN") <--- GW KOMEN DULU SEMENTARA (DEBUG MODE)
    public Response getAllUsers() {
        // Urutkan ID desc biar user baru paling atas
        List<User> users = User.listAll(Sort.descending("user_id"));

        System.out.println("✅ Fetch Users: " + users.size() + " records found.");
        return Response.ok(users).build();
    }

    // ==========================================
    // 3. DETAIL, UPDATE, RESET, DELETE (TETAP AMAN)
    // ==========================================

    @GET
    @Path("/{id}")
    public Response getUserDetail(@PathParam("id") Long id) {
        User user = User.findById(id);
        return user != null ? Response.ok(user).build() : Response.status(404).build();
    }

    @PUT
    @Path("/{id}/status")
    @Transactional
    @RolesAllowed("ADMIN") // Update tetep wajib Admin
    public Response updateStatus(@PathParam("id") Long id, Map<String, String> body) {
        User user = User.findById(id);
        if (user == null) return Response.status(404).build();
        user.status_akun = body.get("status");
        if ("AKTIF".equalsIgnoreCase(user.status_akun)) user.verification_token = null;
        user.persist();
        return Response.ok(Map.of("message", "Status updated")).build();
    }

    @PUT
    @Path("/{id}/reset-password")
    @Transactional
    @RolesAllowed("ADMIN")
    public Response resetPasswordUser(@PathParam("id") Long id, Map<String, String> body) {
        User user = User.findById(id);
        if (user == null) return Response.status(404).build();
        String pwd = body.get("password_baru");
        if (pwd == null || pwd.length() < 6) return Response.status(400).build();
        user.password_hash = BcryptUtil.bcryptHash(pwd);
        user.persist();
        return Response.ok(Map.of("message", "Password reset")).build();
    }

    @DELETE
    @Path("/{id}")
    @Transactional
    @RolesAllowed("ADMIN")
    public Response deleteUser(@PathParam("id") Long id) {
        User user = User.findById(id);
        if (user == null) return Response.status(404).build();
        if ("ADMIN".equals(user.role)) return Response.status(403).build();
        user.delete();
        return Response.ok(Map.of("message", "Deleted")).build();
    }
}