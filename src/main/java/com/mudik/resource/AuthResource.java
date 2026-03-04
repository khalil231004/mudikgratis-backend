package com.mudik.resource;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.mudik.model.User;
import com.mudik.model.PortalConfig;
import com.mudik.service.AuthService;
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

    // DTO Classes
    public static class RegisterRequest {
        @JsonProperty("nama_lengkap") public String nama_lengkap;
        public String email;       // Diterima tapi tidak dipakai untuk verifikasi
        public String password;
        public String nik;
        @JsonProperty("no_hp") public String no_hp;
        @JsonProperty("jenis_kelamin") public String jenis_kelamin;
    }

    public static class LoginRequest {
        public String nik;         // Login pakai NIK
        public String password;
    }

    // ==========================================
    // 1. REGISTER — Langsung aktif, tanpa kirim email verifikasi
    // ==========================================
    @POST
    @Path("/register")
    @PermitAll
    @Transactional
    public Response register(RegisterRequest req) {
        try {
            // Portal check
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

            // Registrasi — email diteruskan ke service tapi tidak dipakai verifikasi
            authService.registerUser(
                    req.nama_lengkap, req.email, req.password,
                    req.nik, req.no_hp, req.jenis_kelamin
            );

            return Response.ok(Map.of(
                    "status", "SUKSES",
                    "pesan", "Registrasi berhasil! Silakan login."
            )).build();

        } catch (Exception e) {
            return Response.status(400).entity(Map.of("error", e.getMessage())).build();
        }
    }

    // ==========================================
    // 2. LOGIN — Pakai NIK
    // ==========================================
    @POST
    @Path("/login")
    @PermitAll
    public Response login(LoginRequest req) {
        try {
            User user = authService.loginUser(req.nik, req.password);

            SecretKey kunci = new SecretKeySpec(jwtSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            String token = Jwt.issuer(baseUrl)
                    .upn(user.nik != null ? user.nik : String.valueOf(user.user_id))
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
    // 3. REQUEST RESET PASSWORD (tetap ada untuk kompatibilitas)
    // ==========================================
    @POST
    @Path("/request-reset-password")
    @PermitAll
    @Transactional
    public Response requestResetPassword(Map<String, String> body) {
        return Response.status(404).entity(Map.of("error", "Fitur reset password via email tidak tersedia. Hubungi admin.")).build();
    }
}