package com.mudik.resource;

import com.mudik.model.User;
import com.mudik.service.AuthService;
import io.quarkus.mailer.Mail;
import io.quarkus.mailer.Mailer;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import io.smallrye.jwt.build.Jwt;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.HashSet;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;

@Path("/api/auth")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class AuthResource {

    @Inject
    AuthService authService;

    @Inject
    Mailer mailer;

    // --- RAHASIA: Ambil Secret Key dari application.properties / .env ---
    @ConfigProperty(name = "smallrye.jwt.sign.key")
    String jwtSecret;

    // --- DINAMIS: Ambil URL Server dari config (Gak pake IP WiFi hardcode lagi) ---
    @ConfigProperty(name = "app.base.url", defaultValue = "http://localhost:8080")
    String baseUrl;

    public static class RegisterRequest {
        public String nama_lengkap;
        public String email;
        public String password;
        public String nik;
        public String no_hp;
    }

    public static class LoginRequest {
        public String email;
        public String password;
    }

    @POST
    @Path("/register")
    @Transactional
    public Response register(RegisterRequest req) {
        System.out.println(">>> REGISTER: Memproses " + req.email);
        try {
            User userBaru = authService.registerUser(
                    req.nama_lengkap, req.email, req.password, req.nik, req.no_hp
            );

            String token = UUID.randomUUID().toString();
            userBaru.verification_token = token;
            userBaru.status_akun = "BELUM_VERIF";
            userBaru.persist();

            // PAKAI BASE URL DARI CONFIG (Bisa berubah otomatis sesuai server)
            String link = baseUrl + "/api/auth/verify?token=" + token;

            String bodyEmail = "<h1>Halo, " + userBaru.nama_lengkap + "!</h1>"
                    + "<p>Silakan klik tombol di bawah untuk mengaktifkan akun:</p>"
                    + "<a href='" + link + "' style='background:blue; color:white; padding:10px 20px; text-decoration:none; border-radius:5px;'>AKTIFKAN AKUN SAYA</a>";

            mailer.send(Mail.withHtml(userBaru.email, "Verifikasi Akun Mudik", bodyEmail));

            return Response.ok(Map.of(
                    "status", "PENDING",
                    "pesan", "Registrasi Berhasil! Cek email untuk verifikasi."
            )).build();
        } catch (Exception e) {
            return Response.status(400).entity(Map.of("error", e.getMessage())).build();
        }
    }

    @GET
    @Path("/verify")
    @Transactional
    @Produces(MediaType.TEXT_HTML)
    public String verifyAccount(@QueryParam("token") String token) {
        if (token == null) return "<h1>❌ Token Kosong</h1>";
        User user = User.find("verification_token", token).firstResult();
        if (user == null) return "<h1>❌ Link Kadaluarsa</h1>";

        user.status_akun = "AKTIF";
        user.verification_token = null;
        user.persist();

        return "<html><body style='text-align:center; padding-top:50px;'>"
                + "<h1 style='color:green; font-size:40px;'>✅ BERHASIL!</h1>"
                + "<h3>Akun (" + user.email + ") sudah aktif.</h3>"
                + "</body></html>";
    }

    @POST
    @Path("/login")
    public Response login(LoginRequest req) {
        try {
            User user = authService.loginUser(req.email, req.password);

            if ("BELUM_VERIF".equals(user.status_akun)) {
                return Response.status(401).entity(Map.of("error", "Akun belum aktif!")).build();
            }

            SecretKey kunciRahasia = new SecretKeySpec(
                    jwtSecret.getBytes(StandardCharsets.UTF_8),
                    "HmacSHA256"
            );

            String token = Jwt.issuer(baseUrl) // Issuer pake baseUrl biar sinkron
                    .upn(user.email)
                    .groups(new HashSet<>(Arrays.asList("USER")))
                    .claim("id_user", user.user_id) // Pastikan field-nya bener (id atau user_id)
                    .claim("nama", user.nama_lengkap)
                    .expiresIn(3600)
                    .sign(kunciRahasia);

            return Response.ok(Map.of(
                    "status", "LOGIN_SUKSES",
                    "token", token,
                    "nama", user.nama_lengkap
            )).build();

        } catch (Exception e) {
            return Response.status(401).entity(Map.of("error", e.getMessage())).build();
        }
    }
}