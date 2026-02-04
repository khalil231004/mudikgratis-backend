package com.mudik.resource;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.mudik.model.User;
import com.mudik.service.AuthService;
import io.quarkus.elytron.security.common.BcryptUtil;

import java.time.LocalDateTime;

import io.quarkus.mailer.Mail;
import io.quarkus.mailer.Mailer;
import io.smallrye.jwt.build.Jwt;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Path("/api/auth")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RolesAllowed("USER")
public class AuthResource {

    @Inject
    AuthService authService;

    @Inject
    Mailer mailer;

    @ConfigProperty(name = "smallrye.jwt.sign.key")
    String jwtSecret;

    @ConfigProperty(name = "app.base.url", defaultValue = "http://localhost:8080")
    String baseUrl;

    String frontendUrl = "http://localhost:5173";

    public static class RegisterRequest {
        @JsonProperty("nama_lengkap")
        public String nama_lengkap;

        public String email;
        public String password;
        public String nik;

        @JsonProperty("no_hp")
        public String no_hp;

        @JsonProperty("jenis_kelamin")
        public String jenis_kelamin;
    }

    public static class LoginRequest {
        public String email;
        public String password;
    }

    @POST
    @Path("/register")
    @Transactional
    public Response register(RegisterRequest req) {
        System.out.println(">>> REGISTER: " + req.email + " | Gender: " + req.jenis_kelamin); // Debug Print
        try {
            User userBaru = authService.registerUser(
                    req.nama_lengkap, req.email, req.password,
                    req.nik, req.no_hp, req.jenis_kelamin
            );

            String token = UUID.randomUUID().toString();
            userBaru.verification_token = token;
            userBaru.status_akun = "BELUM_VERIF";
            userBaru.persist();

            String link = baseUrl + "/api/auth/verify?token=" + token;
            String bodyEmail = templateEmailVerifikasi(userBaru.nama_lengkap, link);

            mailer.send(Mail.withHtml(userBaru.email, "Aktivasi Akun Mudik", bodyEmail));

            return Response.ok(Map.of(
                    "status", "PENDING",
                    "pesan", "Registrasi Berhasil! Cek email untuk verifikasi."
            )).build();

        } catch (IllegalArgumentException e) {
            return Response.status(400).entity(Map.of("error", e.getMessage())).build();
        } catch (Exception e) {
            e.printStackTrace();
            return Response.status(500).entity(Map.of("error", "Gagal server: " + e.getMessage())).build();
        }
    }

    @GET
    @Path("/verify")
    @Transactional
    @Produces(MediaType.TEXT_HTML)
    public String verifyAccount(@QueryParam("token") String token) {
        if (token == null) return "<h1>Token Kosong</h1>";

        User user = User.find("verification_token", token).firstResult();
        if (user == null) return "<h1>Link Invalid / Sudah Dipakai</h1><a href='" + frontendUrl + "/login'>Login</a>";

        user.status_akun = "AKTIF";
        user.verification_token = null;
        user.persist();

        return """
                    <html>
                    <head>
                        <style>
                            body { font-family: sans-serif; display: flex; justify-content: center; align-items: center; height: 100vh; background: #f0fdf4; }
                            .card { background: white; padding: 40px; border-radius: 10px; box-shadow: 0 4px 6px rgba(0,0,0,0.1); text-align: center; }
                            .btn { background: #16a34a; color: white; padding: 10px 20px; text-decoration: none; border-radius: 5px; font-weight: bold; margin-top: 20px; display: inline-block;}
                        </style>
                    </head>
                    <body>
                        <div class='card'>
                            <h1 style='color:#16a34a'>✅ Verifikasi Berhasil!</h1>
                            <p>Akun <b>%s</b> telah aktif.</p>
                            <a href='%s/login' class='btn'>LOGIN SEKARANG</a>
                        </div>
                    </body>
                    </html>
                """.formatted(user.email, frontendUrl);
    }

    @POST
    @Path("/login")
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

        } catch (IllegalArgumentException e) {
            return Response.status(401).entity(Map.of("error", e.getMessage())).build();
        } catch (Exception e) {
            e.printStackTrace();
            return Response.status(500).entity(Map.of("error", "Server Error")).build();
        }


    }

    private String templateEmailVerifikasi(String nama, String link) {
        return """
                    <!DOCTYPE html>
                    <html>
                    <head>
                        <meta charset="UTF-8">
                        <meta name="viewport" content="width=device-width, initial-scale=1.0">
                    </head>
                    <body style="margin: 0; padding: 0; background-color: #f3f4f6; font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif;">
                        <table role="presentation" width="100%%" cellspacing="0" cellpadding="0" border="0">
                            <tr>
                                <td align="center" style="padding: 40px 0;">
                                    <table role="presentation" style="max-width: 600px; width: 100%%; background-color: #ffffff; border-radius: 12px; box-shadow: 0 4px 6px rgba(0, 0, 0, 0.1); overflow: hidden; border: 1px solid #e5e7eb;" cellspacing="0" cellpadding="0" border="0">
                                        <tr>
                                            <td style="background-color: #1e40af; padding: 30px; text-align: center;">
                                                <h1 style="color: #ffffff; margin: 0; font-size: 24px; letter-spacing: 1px;">🚌 MUDIK GRATIS ACEH</h1>
                                                <p style="color: #bfdbfe; margin: 5px 0 0 0; font-size: 14px;">Tahun 2026</p>
                                            </td>
                                        </tr>
                                        <tr>
                                            <td style="padding: 40px 30px;">
                                                <h2 style="color: #1f2937; margin-top: 0; font-size: 20px;">Halo, %s! 👋</h2>
                                                <p style="color: #4b5563; font-size: 16px; line-height: 1.6; margin-bottom: 25px;">
                                                    Terima kasih telah mendaftar di layanan Mudik Gratis. 
                                                    Untuk keamanan dan mulai memesan tiket bus, silakan verifikasi alamat email Anda terlebih dahulu.
                                                </p>
                                                <div style="text-align: center; margin: 35px 0;">
                                                    <a href="%s" style="background-color: #2563EB; color: #ffffff; padding: 14px 32px; text-decoration: none; border-radius: 8px; font-weight: bold; font-size: 16px; display: inline-block; box-shadow: 0 2px 4px rgba(37, 99, 235, 0.3);">
                                                        ✅ Verifikasi Akun Saya
                                                    </a>
                                                </div>
                                                <p style="color: #6b7280; font-size: 14px; margin-top: 30px; border-top: 1px solid #f3f4f6; padding-top: 20px;">
                                                    Jika tombol di atas tidak berfungsi, salin link berikut ke browser Anda:
                                                </p>
                                                <p style="word-break: break-all; font-size: 13px;">
                                                    <a href="%s" style="color: #2563EB;">%s</a>
                                                </p>
                                            </td>
                                        </tr>
                                        <tr>
                                            <td style="background-color: #f9fafb; padding: 20px; text-align: center; border-top: 1px solid #e5e7eb;">
                                                <p style="color: #9ca3af; font-size: 12px; margin: 0;">
                                                    &copy; 2026 Panitia Mudik Gratis Aceh.<br>
                                                    Email ini dikirim secara otomatis, mohon jangan dibalas.
                                                </p>
                                            </td>
                                        </tr>
                                    </table>
                                </td>
                            </tr>
                        </table>
                    </body>
                    </html>
                """.formatted(nama, link, link, link);
    }

    @POST
    @Path("/lupa-password")
    @Transactional
    public Response requestResetPassword(Map<String, String> body) {
        String email = body.get("email");
        if (email == null) return Response.status(400).entity(Map.of("error", "Email wajib diisi")).build();

        User user = User.find("email", email).firstResult();

        if (user == null) {
            return Response.ok(Map.of("pesan", "Jika email terdaftar, link reset akan dikirim.")).build();
        }

        String token = UUID.randomUUID().toString();
        user.reset_token = token;
        user.token_expired_at = LocalDateTime.now().plusHours(1);
        user.persist();

        String linkReset = frontendUrl + "/reset-password?token=" + token;

        String subject = "Reset Password Mudik Gratis";
        String bodyEmail = """
                    Halo %s,<br><br>
                    Anda meminta reset password. Klik tombol di bawah untuk membuat password baru:<br><br>
                    <a href="%s" style="background:#dc2626;color:white;padding:10px 20px;text-decoration:none;border-radius:5px;">RESET PASSWORD</a><br><br>
                    Atau salin link ini: %s <br><br>
                    Link berlaku 1 jam.
                """.formatted(user.nama_lengkap, linkReset, linkReset);

        mailer.send(Mail.withHtml(email, subject, bodyEmail));

        return Response.ok(Map.of("pesan", "Email reset terkirim (Cek Spam jika tidak ada).")).build();
    }

    @POST
    @Path("/reset-password")
    @Transactional
    public Response resetPasswordBaru(Map<String, String> body) {
        String token = body.get("token");
        String passwordBaru = body.get("password_baru");

        if (token == null || passwordBaru == null) {
            return Response.status(400).entity(Map.of("error", "Token dan Password Baru wajib diisi!")).build();
        }

        User user = User.find("reset_token = ?1 AND token_expired_at > ?2", token, LocalDateTime.now()).firstResult();

        if (user == null) {
            return Response.status(400).entity(Map.of("error", "Token tidak valid atau sudah kadaluarsa.")).build();
        }

        user.password_hash = BcryptUtil.bcryptHash(passwordBaru);
        user.reset_token = null;
        user.token_expired_at = null;
        user.persist();

        return Response.ok(Map.of("pesan", "Password berhasil diubah! Silakan login.")).build();
    }
}