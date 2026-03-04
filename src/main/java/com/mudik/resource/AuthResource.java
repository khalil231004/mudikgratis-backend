package com.mudik.resource;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.mudik.model.User;
import com.mudik.model.PortalConfig;
import com.mudik.service.AuthService;
import io.quarkus.elytron.security.common.BcryptUtil;
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

    @ConfigProperty(name = "app.base.url", defaultValue = "https://dishubosrm.acehprov.go.id")
    String baseUrl;

    @ConfigProperty(name = "app.frontend.url", defaultValue = "https://seulamat.dishubaceh.com")
    String frontendUrl;

    @ConfigProperty(name = "quarkus.mailer.username")
    String mailUsername;

    @ConfigProperty(name = "mail.from.name", defaultValue = "Mudik Gratis Aceh")
    String mailFromName;

    // DTO Classes
    public static class RegisterRequest {
        @JsonProperty("nama_lengkap") public String nama_lengkap;
        public String email;           // opsional — boleh null/kosong
        public String password;
        public String nik;
        @JsonProperty("no_hp") public String no_hp;
        @JsonProperty("jenis_kelamin") public String jenis_kelamin;
    }

    public static class LoginRequest {
        public String email;
        public String password;
    }

    public static class ResetPasswordRequest {
        public String token;
        @JsonProperty("new_password") public String newPassword;
    }

    // ==========================================
    // 1. REGISTER — Langsung aktif tanpa verifikasi email
    // ==========================================
    @POST
    @Path("/register")
    @PermitAll
    @Transactional
    public Response register(RegisterRequest req) {
        try {
            // ── PORTAL CHECK ─────────────────────────────────────────
            PortalConfig portalCfg = PortalConfig.getInstance();
            if (!Boolean.TRUE.equals(portalCfg.sesi_aktif)) {
                return Response.status(403).entity(Map.of(
                        "error", portalCfg.pesan_sesi_berakhir != null
                                ? portalCfg.pesan_sesi_berakhir
                                : "Program Mudik Gratis telah berakhir.",
                        "portal_type", "SESI_BERAKHIR"
                )).build();
            }
            if (!Boolean.TRUE.equals(portalCfg.portal_register_open)) {
                return Response.status(403).entity(Map.of(
                        "error", portalCfg.pesan_register_tutup != null
                                ? portalCfg.pesan_register_tutup
                                : "Pendaftaran akun baru ditutup.",
                        "portal_type", "REGISTER_TUTUP"
                )).build();
            }
            // ── END PORTAL CHECK ─────────────────────────────────────

            // Register user — email boleh null, akan di-generate dari NIK
            User userBaru = authService.registerUser(
                    req.nama_lengkap, req.email, req.password,
                    req.nik, req.no_hp, req.jenis_kelamin
            );

            // Langsung generate JWT agar user bisa auto-login setelah register
            SecretKey kunci = new SecretKeySpec(jwtSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            String token = Jwt.issuer(baseUrl)
                    .upn(userBaru.email)
                    .groups(new HashSet<>(List.of(userBaru.role)))
                    .claim("id_user", userBaru.user_id)
                    .claim("nama", userBaru.nama_lengkap)
                    .expiresIn(3600 * 24)
                    .sign(kunci);

            return Response.ok(Map.of(
                    "status", "REGISTER_SUKSES",
                    "pesan", "Akun berhasil dibuat!",
                    "token", token,
                    "role", userBaru.role,
                    "nama", userBaru.nama_lengkap
            )).build();

        } catch (Exception e) {
            return Response.status(400).entity(Map.of("error", e.getMessage())).build();
        }
    }

    // ==========================================
    // 2. VERIFIKASI AKUN (tetap ada untuk backward compat)
    // ==========================================
    @GET
    @Path("/verify")
    @PermitAll
    @Transactional
    public Response verifyAccount(@QueryParam("token") String token) {
        if (token == null) {
            return Response.seeOther(URI.create(frontendUrl + "/login?error=token_missing")).build();
        }

        User user = User.find("verification_token", token).firstResult();

        if (user == null) {
            return Response.seeOther(URI.create(frontendUrl + "/login?error=invalid_token")).build();
        }

        user.status_akun = "AKTIF";
        user.verification_token = null;
        user.persist();

        return Response.seeOther(URI.create(frontendUrl + "/login?status=verified")).build();
    }

    // ==========================================
    // 3. LOGIN
    // ==========================================
    @POST
    @Path("/login")
    @PermitAll
    public Response login(LoginRequest req) {
        try {
            User user = authService.loginUser(req.email, req.password);

            if ("BELUM_VERIF".equals(user.status_akun)) {
                return Response.status(401).entity(Map.of("error", "Akun belum aktif! Hubungi admin.")).build();
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

    // ==========================================
    // 4. REQUEST RESET PASSWORD
    // ==========================================
    @POST
    @Path("/request-reset-password")
    @PermitAll
    @Transactional
    public Response requestResetPassword(Map<String, String> body) {
        String email = body.get("email");
        if (email == null) return Response.status(400).entity(Map.of("error", "Email wajib diisi")).build();

        User user = User.find("email", email).firstResult();
        if (user == null) {
            return Response.status(404).entity(Map.of("error", "Email tidak terdaftar")).build();
        }

        String token = UUID.randomUUID().toString();
        user.verification_token = token;
        user.persist();

        String linkFrontend = frontendUrl + "/reset-password?token=" + token;
        String bodyEmail = templateEmailReset(user.nama_lengkap, linkFrontend);

        mailer.send(Mail.withHtml(user.email, "Reset Password Mudik Gratis", bodyEmail)
                .setFrom(mailFromName + " <" + mailUsername + ">"));

        return Response.ok(Map.of("pesan", "Link reset password telah dikirim ke email Anda.")).build();
    }

    // ==========================================
    // 5. EKSEKUSI RESET PASSWORD h
    // ==========================================
    @POST
    @Path("/reset-password")
    @PermitAll
    @Transactional
    public Response resetPasswordFinish(ResetPasswordRequest req) {
        if (req.token == null || req.newPassword == null) {
            return Response.status(400).entity(Map.of("error", "Data tidak lengkap")).build();
        }

        User user = User.find("verification_token", req.token).firstResult();

        if (user == null) {
            return Response.status(400).entity(Map.of("error", "Token tidak valid atau kadaluarsa")).build();
        }

        user.password_hash = BcryptUtil.bcryptHash(req.newPassword);
        user.verification_token = null;
        user.persist();

        return Response.ok(Map.of("pesan", "Password berhasil diubah! Silakan login.")).build();
    }

    // ==========================================
    // TEMPLATE EMAIL
    // ==========================================
    private String templateEmailReset(String nama, String link) {
        return """
            <!DOCTYPE html><html><body style='font-family:sans-serif;background:#fee2e2;padding:20px;'>
            <div style='max-width:600px;margin:auto;background:white;padding:30px;border-radius:10px;text-align:center;border-top: 5px solid #dc2626;'>
                <h1 style='color:#dc2626;'>🔒 Reset Password</h1>
                <p>Halo <b>%s</b>,</p>
                <p>Kami menerima permintaan untuk mereset password akun Mudik Gratis Anda.</p>
                <p>Klik tombol di bawah untuk membuat password baru:</p>
                <a href='%s' style='display:inline-block;background:#dc2626;color:white;padding:12px 24px;text-decoration:none;border-radius:5px;margin:20px 0;'>Ganti Password</a>
                <p style='font-size:12px;color:#666;'>Jika Anda tidak meminta ini, abaikan saja email ini.</p>
            </div></body></html>
            """.formatted(nama, link);
    }

    @GET
    @Path("/test-email")
    @PermitAll
    public Response testEmail(@QueryParam("target") String target) {
        try {
            mailer.send(Mail.withText(target, "TEST VPS", "Email tembus bos!")
                    .setFrom(mailFromName + " <" + mailUsername + ">"));
            return Response.ok(Map.of("pesan", "Terkirim ke " + target)).build();
        } catch (Exception e) {
            return Response.serverError().entity(Map.of("error", e.getMessage())).build();
        }
    }
}