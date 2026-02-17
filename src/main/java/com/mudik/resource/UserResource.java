package com.mudik.resource;

import com.mudik.model.User;
import com.mudik.service.WhatsAppService;
import io.quarkus.elytron.security.common.BcryptUtil;
import io.quarkus.security.Authenticated;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.Map;
import java.util.UUID;
import java.util.HashMap;

@Path("/api/user")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
// 🔥 @Authenticated dihapus dari sini agar ada method yang bisa diakses publik
public class UserResource {

    @Inject
    SecurityIdentity identity;

    @Inject
    WhatsAppService whatsAppService; // 🔥 Inject WA Service untuk kirim info password

    // 1. LIHAT PROFIL SENDIRI
    @GET
    @Path("/profile")
    @Authenticated // 🔒 Hanya untuk yang sudah login
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

    // 2. USER GANTI PASSWORD SAAT LOGIN
    @POST
    @Path("/change-password")
    @Authenticated // 🔒 Hanya untuk yang sudah login
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

    // 🔥 3. FITUR LUPA PASSWORD (SELF SERVICE)
    @POST
    @Path("/forgot-password")
    @Transactional
    public Response forgotPassword(Map<String, String> body) {
        String nik = body.get("nik");
        String noHp = body.get("no_hp");

        // Cari user yang NIK dan No HP-nya cocok
        User user = User.find("nik = ?1 AND no_hp = ?2", nik, noHp).firstResult();

        if (user == null) {
            return Response.status(404).entity(Map.of("error", "Data NIK atau Nomor HP tidak ditemukan!")).build();
        }

        // Generate Password Baru Sementara (6 Karakter Acak)
        String tempPassword = UUID.randomUUID().toString().substring(0, 6).toUpperCase();

        // Update di Database
        user.password_hash = BcryptUtil.bcryptHash(tempPassword);
        user.persist();

        // Buat Link WA yang mengarah ke nomor user tersebut dengan info password baru
        // Kita buat manual stringnya karena ini bukan objek PendaftaranMudik
        String pesan = "Halo Sdr/i *" + user.nama_lengkap + "*,\n\n" +
                "Kami menerima permintaan reset password untuk akun Mudik Gratis Anda.\n" +
                "Password baru Anda adalah: *" + tempPassword + "*\n\n" +
                "Silakan login kembali dan SEGERA ganti password Anda di menu profil.";

        // Encode pesan WA
        String hpFormat = user.no_hp.replaceAll("[^0-9]", "");
        if (hpFormat.startsWith("0")) hpFormat = "62" + hpFormat.substring(1);

        String waLink = "https://wa.me/" + hpFormat + "?text=" + java.net.URLEncoder.encode(pesan, java.nio.charset.StandardCharsets.UTF_8);

        return Response.ok(Map.of(
                "message", "Password baru telah dibuat.",
                "link_wa", waLink
        )).build();
    }

    // 4. USER EDIT PROFIL
    @PUT
    @Path("/update-profile")
    @Authenticated
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