package com.mudik.resource;

import com.mudik.model.User;
import io.quarkus.elytron.security.common.BcryptUtil;
import io.quarkus.security.Authenticated;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.jwt.JsonWebToken;
import org.jboss.resteasy.reactive.PartType;
import org.jboss.resteasy.reactive.RestForm;
import org.jboss.resteasy.reactive.multipart.FileUpload;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.UUID;

@Path("/api/user")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Authenticated
public class UserResource {

    // FIX: Gunakan JsonWebToken bukan SecurityIdentity untuk baca custom claim "id_user"
    // SecurityIdentity.getAttribute() TIDAK bekerja untuk custom JWT claims di Quarkus!
    @Inject
    JsonWebToken jwt;

    @ConfigProperty(name = "quarkus.http.body.uploads-directory")
    String uploadDir;

    public static class ProfileForm {
        @RestForm @PartType(MediaType.TEXT_PLAIN) public String nama_lengkap;
        @RestForm @PartType(MediaType.TEXT_PLAIN) public String nik;
        @RestForm @PartType(MediaType.TEXT_PLAIN) public String no_hp;
        @RestForm("foto_profil") public FileUpload fotoProfil;
    }

    /**
     * HELPER: Ambil User dari JWT.
     * Prioritas: claim "id_user" → fallback ke email (subject/upn).
     */
    private User getUserFromJwt() {
        // Coba baca claim id_user yang di-set saat login
        Object idClaim = jwt.getClaim("id_user");
        if (idClaim != null) {
            try {
                Long uid = (idClaim instanceof Number)
                        ? ((Number) idClaim).longValue()
                        : Long.parseLong(idClaim.toString());
                User user = User.findById(uid);
                if (user != null) return user;
            } catch (NumberFormatException ignored) {}
        }
        // Fallback: cari via email (UPN = subject JWT)
        String email = jwt.getName(); // getName() mengembalikan UPN/subject
        return User.find("email", email).firstResult();
    }

    // 1. LIHAT PROFIL
    @GET
    @Path("/profile")
    public Response getMyProfile() {
        try {
            User user = getUserFromJwt();
            if (user == null)
                return Response.status(404).entity(Map.of("error", "User tidak ditemukan")).build();

            return Response.ok(Map.of(
                    "nama",       user.nama_lengkap  != null ? user.nama_lengkap  : "",
                    "email",      user.email         != null ? user.email         : "",
                    "nik",        user.nik           != null ? user.nik           : "",
                    "no_hp",      user.no_hp         != null ? user.no_hp         : "",
                    "foto_profil",user.foto_profil   != null ? user.foto_profil   : "",
                    "role",       user.role          != null ? user.role          : ""
            )).build();
        } catch (Exception e) {
            return Response.status(500).entity(Map.of("error", e.getMessage())).build();
        }
    }

    // 2. GANTI PASSWORD (DENGAN CEK PASSWORD LAMA)
    @POST
    @Path("/change-password")
    @Transactional
    public Response changePassword(Map<String, String> body) {
        User user = getUserFromJwt();
        if (user == null) return Response.status(401).entity(Map.of("error", "User tidak ditemukan")).build();

        String passLama = body.get("password_lama");
        String passBaru = body.get("password_baru");

        if (passLama == null || passBaru == null)
            return Response.status(400).entity(Map.of("error", "Password lama dan baru wajib diisi")).build();

        if (!BcryptUtil.matches(passLama, user.password_hash)) {
            return Response.status(400).entity(Map.of("error", "Password lama salah!")).build();
        }

        if (passBaru.length() < 6)
            return Response.status(400).entity(Map.of("error", "Password baru minimal 6 karakter")).build();

        user.password_hash = BcryptUtil.bcryptHash(passBaru);
        user.persist();

        return Response.ok(Map.of("message", "Password berhasil diubah!")).build();
    }

    // 3. UPDATE PROFIL (NAMA, NIK, HP, FOTO)
    @PUT
    @Path("/update-profile")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Transactional
    public Response updateProfile(ProfileForm form) {
        User user = getUserFromJwt();
        if (user == null)
            return Response.status(404).entity(Map.of("error", "User tidak ditemukan")).build();

        if (form.nama_lengkap != null && !form.nama_lengkap.isBlank()) user.nama_lengkap = form.nama_lengkap;
        if (form.nik         != null && !form.nik.isBlank())          user.nik           = form.nik;
        if (form.no_hp       != null && !form.no_hp.isBlank())        user.no_hp         = form.no_hp;

        if (form.fotoProfil != null && form.fotoProfil.fileName() != null && !form.fotoProfil.fileName().isEmpty()) {
            try {
                String fileName = "profile-" + user.user_id + "-" + UUID.randomUUID().toString().substring(0, 8) + ".jpg";
                File dest = new File(uploadDir, fileName);
                Files.move(form.fotoProfil.uploadedFile(), dest.toPath(), StandardCopyOption.REPLACE_EXISTING);
                user.foto_profil = "uploads/" + fileName;
            } catch (IOException e) {
                return Response.status(500).entity(Map.of("error", "Gagal upload foto")).build();
            }
        }

        user.persist();
        return Response.ok(Map.of(
                "message",   "Profil berhasil diperbarui!",
                "foto_baru", user.foto_profil != null ? user.foto_profil : ""
        )).build();
    }
}
