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

@Path("/api/admin/users")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class AdminUserResource {

    // 1. GET ALL USERS (FIXED: Mapping 'user_id' jadi 'id')
    @GET
    public Response getAllUsers() {
        List<User> users = User.listAll(Sort.descending("user_id"));

        List<Map<String, Object>> result = new ArrayList<>();
        for (User u : users) {
            Map<String, Object> map = new HashMap<>();
            map.put("id", u.user_id); // 🔥 INI KUNCINYA BIAR GAK UNDEFINED
            map.put("user_id", u.user_id);
            map.put("email", u.email);
            map.put("nama_lengkap", u.nama_lengkap);
            map.put("nik", u.nik);
            map.put("no_hp", u.no_hp);
            map.put("role", u.role);
            map.put("status_akun", u.status_akun);
            map.put("created_at", u.created_at != null ? u.created_at.toString() : "-");
            result.add(map);
        }

        return Response.ok(result).build();
    }

    @GET
    @Path("/stats")
    public Response getUserStats() {
        Map<String, Long> stats = new HashMap<>();
        try {
            stats.put("total_user", User.count());
            stats.put("user_aktif", User.count("status_akun", "AKTIF"));
        } catch (Exception e) { stats.put("error", 0L); }
        return Response.ok(stats).build();
    }

    // 2. UPDATE STATUS (FIX: Tambahkan fungsi ini yg sebelumnya hilang)
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

    // 3. RESET PASSWORD (FIX: Tambahkan fungsi ini yg sebelumnya hilang)
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

    @DELETE
    @Path("/{id}")
    @Transactional
    public Response deleteUser(@PathParam("id") Long id) {
        User user = User.findById(id);
        if (user == null) return Response.status(404).build();
        // Cegah hapus admin sendiri
        if ("ADMIN".equalsIgnoreCase(user.role)) return Response.status(403).build();

        user.delete();
        return Response.ok(Map.of("message", "User dihapus")).build();
    }
}