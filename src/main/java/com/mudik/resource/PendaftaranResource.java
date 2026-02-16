package com.mudik.resource;

import com.mudik.model.PendaftaranMudik;
import com.mudik.model.Rute;
import com.mudik.model.User;
import com.mudik.service.PendaftaranService;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.jboss.resteasy.reactive.RestForm;
import org.jboss.resteasy.reactive.multipart.FileUpload;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Path("/api/pendaftaran")
public class PendaftaranResource {

    @Inject
    PendaftaranService pendaftaranService;

    // DTO Wrapper (Sesuai kode lama + field lengkap)
    public static class PendaftaranMultipartForm {
        @RestForm("nama_peserta") public List<String> nama_peserta;
        @RestForm("nik_peserta") public List<String> nik_peserta;
        @RestForm("jenis_kelamin") public List<String> jenis_kelamin;
        @RestForm("tanggal_lahir") public List<String> tanggal_lahir;
        @RestForm("jenis_identitas") public List<String> jenis_identitas;
        @RestForm("jenis_barang") public List<String> jenis_barang;
        @RestForm("ukuran_barang") public List<String> ukuran_barang;
        @RestForm("alamat_rumah") public List<String> alamat_rumah;
        @RestForm("no_hp_peserta") public List<String> no_hp_peserta;
        @RestForm("fotoBukti") public List<FileUpload> fotoBukti;
    }

    // 1. GET RIWAYAT (AMAN - Kode Lama)
    @GET
    @Path("/riwayat")
    @Produces(MediaType.APPLICATION_JSON)
    public Response riwayatPendaftaran(@HeaderParam("userId") Long userId) {
        if (userId == null) return Response.status(401).entity(Map.of("error", "Unauthorized")).build();

        List<PendaftaranMudik> list = PendaftaranMudik.list("user.user_id = ?1 ORDER BY created_at DESC", userId);
        List<Map<String, Object>> result = new ArrayList<>();

        for (PendaftaranMudik p : list) {
            Map<String, Object> map = new HashMap<>();
            map.put("pendaftaran_id", p.pendaftaran_id);
            map.put("kode_booking", p.kode_booking != null ? p.kode_booking : "-");
            map.put("status_pendaftaran", p.status_pendaftaran);
            map.put("nama_peserta", p.nama_peserta);

            // Tambahan info buat Frontend Edit
            map.put("nik_peserta", p.nik_peserta);
            map.put("alasan_tolak", "Silakan perbaiki data dan upload ulang KTP."); // Pesan default

            if (p.rute != null) {
                map.put("tujuan", p.rute.tujuan);
                map.put("tanggal_keberangkatan", p.rute.getFormattedDate());
            } else { map.put("tujuan", "-"); }

            map.put("nama_bus", (p.kendaraan != null) ? p.kendaraan.nama_armada : "Menunggu Plotting");
            result.add(map);
        }
        return Response.ok(result).build();
    }

    // 2. POST DAFTAR (Panggil Service - AMAN)
    @POST
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Produces(MediaType.APPLICATION_JSON)
    public Response daftarBatch(@HeaderParam("userId") Long userId, @QueryParam("rute_id") Long ruteId, PendaftaranMultipartForm form) {
        try {
            if (userId == null) return Response.status(400).entity(Map.of("error", "Login dulu!")).build();
            if (ruteId == null) return Response.status(400).entity(Map.of("error", "Pilih rute!")).build();
            if (form.nama_peserta == null || form.nama_peserta.isEmpty()) return Response.status(400).entity(Map.of("error", "Peserta kosong!")).build();

            User user = User.findById(userId);
            Rute rute = Rute.findById(ruteId);
            if (user == null || rute == null) return Response.status(404).entity(Map.of("error", "Data tidak valid")).build();

            // 🔥 LOGIC SERVICE (Validasi NIK, Kuota, Upload)
            pendaftaranService.prosesPendaftaranWeb(user, rute, form);

            return Response.ok(Map.of("status", "BERHASIL", "pesan", form.nama_peserta.size() + " peserta terdaftar!")).build();
        } catch (Exception e) {
            return Response.status(400).entity(Map.of("error", e.getMessage())).build();
        }
    }

    // 3. PUT KONFIRMASI (AMAN - Sesuai Frontend Lama)
    @PUT
    @Path("/konfirmasi-kehadiran/{userId}")
    public Response konfirmasiKehadiran(@PathParam("userId") Long userId, Map<String, List<Long>> body) {
        try {
            // Pastikan key-nya "ids_konfirmasi" sesuai dengan Konfirmasi.tsx
            List<Long> ids = body.get("ids_konfirmasi");
            if (ids == null) {
                return Response.status(400).entity(Map.of("error", "Data 'ids_konfirmasi' tidak ditemukan di body")).build();
            }
            // Panggil Service untuk ubah status jadi TERVERIFIKASI/SIAP BERANGKAT
            String pesan = pendaftaranService.prosesKonfirmasi(userId, ids);

            return Response.ok(Map.of("status", "BERHASIL", "message", pesan)).build();
        } catch (Exception e) {
            e.printStackTrace();
            return Response.status(400).entity(Map.of("error", e.getMessage())).build();
        }
    }

    // 4. PUT PERBAIKI DATA (ENDPOINT BARU - FIX POIN 1 & 11)
    @PUT
    @Path("/perbaiki-data/{pendaftaranId}")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    public Response perbaikiData(@PathParam("pendaftaranId") Long pendaftaranId,
                                 @HeaderParam("userId") Long userId,
                                 PendaftaranMultipartForm form) {
        try {
            if (userId == null) return Response.status(401).entity(Map.of("error", "UserId header missing")).build();

            // Panggil Service Edit (Reset ke MENUNGGU VERIFIKASI & Ambil Kuota Lagi)
            pendaftaranService.editPendaftaran(userId, pendaftaranId, form);

            return Response.ok(Map.of(
                    "status", "BERHASIL",
                    "pesan", "Data berhasil diperbaiki dan diajukan kembali. Menunggu Verifikasi."
            )).build();
        } catch (Exception e) {
            return Response.status(400).entity(Map.of("error", e.getMessage())).build();
        }
    }
}