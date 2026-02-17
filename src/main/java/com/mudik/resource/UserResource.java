package com.mudik.resource;

import com.mudik.model.User;
import io.quarkus.elytron.security.common.BcryptUtil;
import io.quarkus.security.Authenticated;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.Map;

@Path("/api/user")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Authenticated // 🔥 KEMBALI DIAMANKAN: Semua endpoint di sini WAJIB Login
public class UserResource {

    @Inject
    SecurityIdentity identity;

    // (Inject WhatsAppService DIHAPUS karena tidak dipakai lagi di sini)

    // ==========================================
    // 1. LIHAT PROFIL SENDIRI
    // ==========================================
    @GET
    @Path("/profile")
    public Response getMyProfile() {
        String email = identity.getPrincipal().getName();
        User user = User.find("email", email).firstResult();

        if (user == null) return Response.status(404).build();

        return Response.ok(Map.of(
                "nama", user.nama_lengkap,
                "email", user.email,
                "nik", user.nik,
                "no_hp", user.no_hp,
                "status", user.status_akun,
                "role", user.role
        )).build();
    }

    // ==========================================
    // 2. USER GANTI PASSWORD (SAAT SUDAH LOGIN)
    // ==========================================
    @POST
    @Path("/change-password")
    @Transactional
    public Response changePassword(Map<String, String> body) {
        String email = identity.getPrincipal().getName();
        User user = User.find("email", email).firstResult();

        String passwordBaru = body.get("password_baru");
        if (user == null || passwordBaru == null) return Response.status(400).build();

        user.password_hash = BcryptUtil.bcryptHash(passwordBaru);
        user.persist();

        return Response.ok(Map.of("message", "Password berhasil diubah!")).build();
    }

    // ==========================================
    // 3. USER EDIT PROFIL
    // ==========================================
    @PUT
    @Path("/update-profile")
    @Transactional
    public Response updateProfile(Map<String, String> body) {
        String email = identity.getPrincipal().getName();
        User user = User.find("email", email).firstResult();

        if (user == null) return Response.status(404).build();

        if (body.containsKey("nama_lengkap")) user.nama_lengkap = body.get("nama_lengkap");
        if (body.containsKey("no_hp")) user.no_hp = body.get("no_hp");

        user.persist();
        return Response.ok(Map.of("message", "Profil berhasil diupdate")).build();
    }
}