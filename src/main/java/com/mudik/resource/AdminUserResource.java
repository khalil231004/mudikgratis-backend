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

    // Fetch User sekarang terbuka sesuai application.properties
    @GET
    public Response getAllUsers() {
        List<User> users = User.listAll(Sort.descending("user_id"));
        return Response.ok(users).build();
    }

    @GET
    @Path("/stats")
    public Response getUserStats() {
        Map<String, Long> stats = new HashMap<>();
        try {
            stats.put("total_user", User.count());
            stats.put("user_aktif", User.count("status_akun", "AKTIF"));
        } catch (Exception e) {
            stats.put("error", 0L);
        }
        return Response.ok(stats).build();
    }

    @DELETE
    @Path("/{id}")
    @Transactional
    @RolesAllowed("ADMIN") // Hapus tetep harus Admin
    public Response deleteUser(@PathParam("id") Long id) {
        User user = User.findById(id);
        if (user == null) return Response.status(404).build();
        if ("ADMIN".equals(user.role)) return Response.status(403).build();
        user.delete();
        return Response.ok(Map.of("message", "Deleted")).build();
    }
}