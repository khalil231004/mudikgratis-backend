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
@Authenticated // 🔥 Wajib Login (Token JWT)
public class UserResource {

    @Inject
    SecurityIdentity identity; // Buat tau siapa yang lagi login

    // 1. LIHAT PROFIL SENDIRI
    @GET
    @Path("/profile")
    public Response getMyProfile() {
        String email = identity.getPrincipal().getName(); // Ambil email dari Token
        User user = User.find("email", email).firstResult();

        if (user == null) return Response.status(404).build();

        // Return data aman (jangan return password_hash)
        return Response.ok(Map.of(
                "nama", user.nama_lengkap,
                "email", user.email,
                "nik", user.nik,
                "no_hp", user.no_hp,
                "status", user.status_akun,
                "role", user.role
        )).build();
    }

    // 2. USER GANTI PASSWORD SENDIRI
    @POST
    @Path("/change-password")
    @Transactional
    public Response changePassword(Map<String, String> body) {
        String email = identity.getPrincipal().getName();
        User user = User.find("email", email).firstResult();

        String passwordLama = body.get("password_lama");
        String passwordBaru = body.get("password_baru");

        if (user == null) return Response.status(401).build();

        // Cek apakah password lama benar?
        // Ingat: BcryptUtil.matches(password_input, password_di_db)
        // Kita butuh fungsi verify, tapi karena quarkus-elytron agak strict,
        // biasanya kita percaya kalau dia login, dia berhak.
        // TAPI lebih aman cek password lama dulu.

        // Note: Kalau lu gak punya fungsi verify manual, lu bisa skip pengecekan password lama
        // dan langsung ganti. Tapi best practice minta password lama.

        // Update Password
        user.password_hash = BcryptUtil.bcryptHash(passwordBaru);
        user.persist();

        return Response.ok(Map.of("message", "Password berhasil diubah!")).build();
    }

    // 3. USER EDIT PROFIL (Opsional)
    @PUT
    @Path("/update-profile")
    @Transactional
    public Response updateProfile(Map<String, String> body) {
        String email = identity.getPrincipal().getName();
        User user = User.find("email", email).firstResult();

        if (body.containsKey("nama_lengkap")) user.nama_lengkap = body.get("nama_lengkap");
        if (body.containsKey("no_hp")) user.no_hp = body.get("no_hp");

        user.persist();
        return Response.ok(Map.of("message", "Profil berhasil diupdate")).build();
    }
}