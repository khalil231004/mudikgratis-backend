package com.mudik.resource;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.mudik.model.User;
import com.mudik.service.AuthService;
import io.quarkus.mailer.Mail;
import io.quarkus.mailer.Mailer;
import io.smallrye.jwt.build.Jwt;
import jakarta.annotation.security.PermitAll;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Path("/api/auth")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class AuthResource {

    @Inject
    AuthService authService;

    @Inject
    Mailer mailer;

    @ConfigProperty(name = "smallrye.jwt.sign.key")
    String jwtSecret;

    // URL Backend (VPS) - Dipakai buat bikin link verifikasi di email
    @ConfigProperty(name = "app.base.url", defaultValue = "https://seulamat.dishubaceh.com")
    String baseUrl;

    // 🔥 URL FRONTEND (String Baru) - Dipakai buat redirect user setelah klik link
    @ConfigProperty(name = "app.frontend.url", defaultValue = "https://dishubosrm.acehprov.go.id")
    String frontendUrl;
//b6

    public static class RegisterRequest {
        @JsonProperty("nama_lengkap") public String nama_lengkap;
        public String email;
        public String password;
        public String nik;
        @JsonProperty("no_hp") public String no_hp;
        @JsonProperty("jenis_kelamin") public String jenis_kelamin;
    }

    public static class LoginRequest {
        public String email;
        public String password;
    }

    @POST
    @Path("/register")
    @PermitAll
    @Transactional
    public Response register(RegisterRequest req) {
        try {
            User userBaru = authService.registerUser(
                    req.nama_lengkap, req.email, req.password,
                    req.nik, req.no_hp, req.jenis_kelamin
            );

            String token = UUID.randomUUID().toString();
            userBaru.verification_token = token;
            userBaru.status_akun = "BELUM_VERIF";
            userBaru.persist();

            // Link Verifikasi tetap mengarah ke API Backend dulu buat validasi DB
            String link = baseUrl + "/api/auth/verify?token=" + token;
            String bodyEmail = templateEmailVerifikasi(userBaru.nama_lengkap, link);

            mailer.send(Mail.withHtml(userBaru.email, "Aktivasi Akun Mudik", bodyEmail));

            return Response.ok(Map.of(
                    "status", "PENDING",
                    "pesan", "Registrasi Berhasil! Silakan cek email."
            )).build();

        } catch (Exception e) {
            return Response.status(400).entity(Map.of("error", e.getMessage())).build();
        }
    }

    @GET
    @Path("/verify")
    @PermitAll
    @Transactional
    public Response verifyAccount(@QueryParam("token") String token) {
        if (token == null) {
            // Redirect ke Frontend Login dengan error
            return Response.seeOther(URI.create(frontendUrl + "/login?error=token_missing")).build();
        }

        User user = User.find("verification_token", token).firstResult();

        if (user == null) {
            // Redirect ke Frontend Login dengan error
            return Response.seeOther(URI.create(frontendUrl + "/login?error=invalid_token")).build();
        }

        // Aktifkan User
        user.status_akun = "AKTIF";
        user.verification_token = null;
        user.persist();

        // 🔥 REDIRECT KE FRONTEND UTAMA (DISHUB) SETELAH SUKSES
        return Response.seeOther(URI.create(frontendUrl + "/login?status=verified")).build();
    }

    @POST
    @Path("/login")
    @PermitAll
    public Response login(LoginRequest req) {
        try {
            User user = authService.loginUser(req.email, req.password);

            if ("BELUM_VERIF".equals(user.status_akun)) {
                return Response.status(401).entity(Map.of("error", "Akun belum aktif! Cek email Anda.")).build();
            }

            SecretKey kunci = new SecretKeySpec(jwtSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            String token = Jwt.issuer(baseUrl)
                    .upn(user.email)
                    .groups(new HashSet<>(List.of(user.role)))
                    .claim("id_user", user.user_id)
                    .claim("nama", user.nama_lengkap)
                    .expiresIn(3600 * 24)
                    .sign(kunci);

            return Response.ok(Map.of(
                    "status", "LOGIN_SUKSES",
                    "token", token,
                    "role", user.role,
                    "nama", user.nama_lengkap
            )).build();

        } catch (Exception e) {
            return Response.status(401).entity(Map.of("error", e.getMessage())).build();
        }
    }

    // --- Template Email ---
    private String templateEmailVerifikasi(String nama, String link) {
        return """
            <!DOCTYPE html><html><body style='font-family:sans-serif;background:#f3f4f6;padding:20px;'>
            <div style='max-width:600px;margin:auto;background:white;padding:30px;border-radius:10px;text-align:center;'>
                <h1 style='color:#1e40af;'>🚌 MUDIK GRATIS ACEH</h1>
                <p>Halo <b>%s</b>, silakan verifikasi akun Anda:</p>
                <a href='%s' style='display:inline-block;background:#2563EB;color:white;padding:12px 24px;text-decoration:none;border-radius:5px;margin:20px 0;'>Verifikasi Akun</a>
                <p style='font-size:12px;color:#666;'>Atau copy link ini: %s</p>
            </div></body></html>
            """.formatted(nama, link, link);
    }

    @GET
    @Path("/test-email")
    @PermitAll
    public Response testEmail(@QueryParam("target") String target) {
        try {
            mailer.send(Mail.withText(target, "TEST VPS", "Email tembus bos!"));
            return Response.ok(Map.of("pesan", "Terkirim ke " + target)).build();
        } catch (Exception e) {
            return Response.serverError().entity(Map.of("error", e.getMessage())).build();
        }
    }
}