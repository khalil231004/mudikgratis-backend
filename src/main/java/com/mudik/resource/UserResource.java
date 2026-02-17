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
import org.eclipse.microprofile.config.inject.ConfigProperty;
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
@Authenticated // 🔥 Wajib Login
public class UserResource {

    @Inject
    SecurityIdentity identity;

    @ConfigProperty(name = "quarkus.http.body.uploads-directory")
    String uploadDir;

    // DTO untuk Form Update Profil (Multipart)
    public static class ProfileForm {
        @RestForm @PartType(MediaType.TEXT_PLAIN) public String nama_lengkap;
        @RestForm @PartType(MediaType.TEXT_PLAIN) public String nik;
        @RestForm @PartType(MediaType.TEXT_PLAIN) public String no_hp;
        @RestForm("foto_profil") public FileUpload fotoProfil; // File Gambar
    }

    // 1. LIHAT PROFIL
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
                "foto_profil", user.foto_profil != null ? user.foto_profil : "", // Return URL foto
                "role", user.role
        )).build();
    }

    // 2. GANTI PASSWORD (DENGAN CEK PASSWORD LAMA)
    @POST
    @Path("/change-password")
    @Transactional
    public Response changePassword(Map<String, String> body) {
        String email = identity.getPrincipal().getName();
        User user = User.find("email", email).firstResult();

        String passLama = body.get("password_lama");
        String passBaru = body.get("password_baru");

        if (user == null) return Response.status(401).build();

        // 🔥 Validasi Password Lama
        if (!BcryptUtil.matches(passLama, user.password_hash)) {
            return Response.status(400).entity(Map.of("error", "Password lama salah!")).build();
        }

        user.password_hash = BcryptUtil.bcryptHash(passBaru);
        user.persist();

        return Response.ok(Map.of("message", "Password berhasil diubah!")).build();
    }

    // 3. UPDATE PROFIL (NAMA, NIK, HP, FOTO)
    @PUT
    @Path("/update-profile")
    @Consumes(MediaType.MULTIPART_FORM_DATA) // 🔥 Wajib Multipart
    @Transactional
    public Response updateProfile(ProfileForm form) {
        String email = identity.getPrincipal().getName();
        User user = User.find("email", email).firstResult();
        if (user == null) return Response.status(404).build();

        // Update Text Data
        if (form.nama_lengkap != null && !form.nama_lengkap.isBlank()) user.nama_lengkap = form.nama_lengkap;
        if (form.nik != null && !form.nik.isBlank()) user.nik = form.nik;
        if (form.no_hp != null && !form.no_hp.isBlank()) user.no_hp = form.no_hp;

        // Update Foto Profil (Jika ada upload)
        if (form.fotoProfil != null && form.fotoProfil.fileName() != null && !form.fotoProfil.fileName().isEmpty()) {
            try {
                String fileName = "profile-" + user.user_id + "-" + UUID.randomUUID().toString().substring(0,8) + ".jpg";
                File dest = new File(uploadDir, fileName);

                // Pindahkan file
                Files.move(form.fotoProfil.uploadedFile(), dest.toPath(), StandardCopyOption.REPLACE_EXISTING);

                // Simpan path ke DB
                user.foto_profil = "uploads/" + fileName;
            } catch (IOException e) {
                return Response.status(500).entity(Map.of("error", "Gagal upload foto")).build();
            }
        }

        user.persist();
        return Response.ok(Map.of("message", "Profil berhasil diperbarui!", "foto_baru", user.foto_profil != null ? user.foto_profil : "")).build();
    }
}