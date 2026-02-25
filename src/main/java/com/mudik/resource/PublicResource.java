package com.mudik.resource;

import com.mudik.model.PendaftaranMudik;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.io.File;
import java.util.Map;

@Path("/api/public")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class PublicResource {

    // 🔥 BACA DARI CONFIG JUGA
    @ConfigProperty(name = "quarkus.http.body.uploads-directory")
    String uploadDir;

    @GET
    @Path("/uploads/{filename}")
    @Produces({"image/jpeg", "image/png"})
    public Response getFile(@PathParam("filename") String filename) {
        // Gabungin Path Config + Nama File
        File file = new File(uploadDir + File.separator + filename);

        if (!file.exists()) {
            return Response.status(404).build();
        }
        return Response.ok(file).build();
    }

    // ✅ POIN 7: Endpoint untuk cek jumlah total NIK sudah terdaftar (public, untuk Register page)
    @GET
    @Path("/stats-pendaftar")
    public Response statsPendaftar() {
        long totalNik = com.mudik.model.PendaftaranMudik.count(
                "status_pendaftaran NOT IN ('DIBATALKAN', 'DITOLAK')");
        long totalAkun = com.mudik.model.User.count();
        return Response.ok(Map.of(
                "total_nik_terdaftar", totalNik,
                "total_akun", totalAkun
        )).build();
    }

    // ✅ Endpoint untuk Frontend mengecek status via UUID (Tanpa Login)
    @GET
    @Path("/cek-status/{uuid}")
    public Response cekStatusViaUuid(@PathParam("uuid") String uuid) {
        PendaftaranMudik p = PendaftaranMudik.find("uuid", uuid).firstResult();
        if (p == null) {
            return Response.status(404).entity(Map.of("error", "Data tidak ditemukan")).build();
        }

        return Response.ok(Map.of(
                "nama", p.nama_peserta,
                "status", p.status_pendaftaran,
                "bus", (p.kendaraan != null) ? p.kendaraan.nama_armada : "-",
                "kursi", (p.nomor_kursi != null) ? p.nomor_kursi : "-"
        )).build();
    }

    // ✅ Endpoint Konfirmasi Kehadiran via UUID (Aman dari tebak-tebakan ID)
    @PUT
    @Path("/konfirmasi/{uuid}")
    @Transactional
    public Response konfirmasiH3(@PathParam("uuid") String uuid) {
        PendaftaranMudik p = PendaftaranMudik.find("uuid", uuid).firstResult();
        if (p == null) return Response.status(404).entity(Map.of("error", "Link tidak valid")).build();

        if (!"DITERIMA".equals(p.status_pendaftaran)) {
            return Response.status(400).entity(Map.of("error", "Status Anda bukan DITERIMA, tidak bisa konfirmasi.")).build();
        }

        p.status_pendaftaran = "TERKONFIRMASI";
        p.persist();

        return Response.ok(Map.of("status", "BERHASIL", "message", "Terima kasih, kehadiran terkonfirmasi!")).build();
    }
}