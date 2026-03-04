package com.mudik.resource;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.mudik.model.User;
import com.mudik.model.PortalConfig;
import com.mudik.service.AuthService;
import io.quarkus.elytron.security.common.BcryptUtil;
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
import java.nio.charset.StandardCharsets;
import java.util.*;


@Path("/api/auth")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class AuthResource {

    @Inject
    AuthService authService;

    @ConfigProperty(name = "smallrye.jwt.sign.key")
    String jwtSecret;

    @ConfigProperty(name = "app.base.url", defaultValue = "https://dishubosrm.acehprov.go.id")
    String baseUrl;

    // DTO Register — tanpa email
    public static class RegisterRequest {
        @JsonProperty("nama_lengkap") public String nama_lengkap;
        public String password;
        public String nik;
        @JsonProperty("no_hp") public String no_hp;
        @JsonProperty("jenis_kelamin") public String jenis_kelamin;
    }

    /**
     * DTO Login fleksibel:
     * - User biasa  → kirim field "nik" + "password"
     * - Admin       → kirim field "email" + "password"
     */
    public static class LoginRequest {
        public String nik;
        public String email;
        public String password;
    }

    public static class ResetPasswordRequest {
        public String token;
        @JsonProperty("new_password") public String newPassword;
    }

    // ==========================================
    // 1. REGISTER — Status langsung AKTIF, tanpa email
    // ==========================================
    @POST
    @Path("/register")
    @PermitAll
    @Transactional
    public Response register(RegisterRequest req) {
        try {
            // ── PORTAL CHECK ─────────────────────────────────────
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
            // ── END PORTAL CHECK ──────────────────────────────────

            // FIX: authService.registerUser sudah persist() di dalamnya.
            // Jangan panggil persist() lagi di sini — itu penyebab bug NIK duplikat palsu.
            authService.registerUser(
                    req.nama_lengkap, req.password,
                    req.nik, req.no_hp, req.jenis_kelamin
            );

            return Response.ok(Map.of(
                    "status", "SUKSES",
                    "pesan", "Registrasi Berhasil! Silakan login."
            )).build();

        } catch (Exception e) {
            return Response.status(400).entity(Map.of("error", e.getMessage())).build();
        }
    }

    // ==========================================
    // 2. LOGIN — Support NIK (user) dan Email (admin)
    // ==========================================
    @POST
    @Path("/login")
    @PermitAll
    public Response login(LoginRequest req) {
        try {
            // Gunakan email jika ada (admin), fallback ke nik (user biasa)
            String identifier = (req.email != null && !req.email.isBlank())
                    ? req.email
                    : req.nik;

            if (identifier == null || identifier.isBlank()) {
                return Response.status(400).entity(Map.of("error", "NIK atau Email wajib diisi.")).build();
            }

            User user = authService.loginUser(identifier, req.password);

            if ("BANNED".equals(user.status_akun)) {
                return Response.status(403).entity(Map.of(
                        "error", "Akun Anda telah diblokir. Hubungi admin."
                )).build();
            }

            // upn pakai NIK untuk user, email untuk admin
            String upn = (user.nik != null && !user.nik.isBlank()) ? user.nik : user.email;

            SecretKey kunci = new SecretKeySpec(jwtSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            String token = Jwt.issuer(baseUrl)
                    .upn(upn)
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
    // 3. RESET PASSWORD
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
}
