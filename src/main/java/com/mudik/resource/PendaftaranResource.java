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

    // ================================================================
    // DTO: Form multipart pendaftaran
    // ================================================================
    public static class PendaftaranMultipartForm {
        @RestForm("nama_peserta")    public List<String>     nama_peserta;
        @RestForm("nik_peserta")     public List<String>     nik_peserta;
        @RestForm("jenis_kelamin")   public List<String>     jenis_kelamin;
        @RestForm("tanggal_lahir")   public List<String>     tanggal_lahir;
        @RestForm("jenis_identitas") public List<String>     jenis_identitas;
        @RestForm("alamat_rumah")    public List<String>     alamat_rumah;
        @RestForm("no_hp_peserta")   public List<String>     no_hp_peserta;
        @RestForm("fotoBukti")       public List<FileUpload> fotoBukti;
    }

    // ================================================================
    // HELPER: Resolve userId dari header (bisa berupa angka atau UUID)
    // ================================================================
    private Long resolveUserId(String idStr) {
        if (idStr == null || idStr.isEmpty() || "undefined".equals(idStr)) {
            throw new WebApplicationException("User ID tidak valid/kosong", 400);
        }

        // Header duplikat kadang dikirim sebagai "30, 30" — ambil nilai pertama
        String cleaned = idStr.split(",")[0].trim().replace("\"", "");

        if (cleaned.isEmpty() || "undefined".equals(cleaned)) {
            throw new WebApplicationException("User ID tidak valid/kosong", 400);
        }

        try {
            return Long.parseLong(cleaned);
        } catch (NumberFormatException e) {
            // Bukan angka → coba cari sebagai UUID pendaftaran
            PendaftaranMudik p = PendaftaranMudik.find("uuid", cleaned).firstResult();
            if (p != null && p.user != null) {
                return p.user.user_id;
            }
            throw new WebApplicationException("Data User tidak ditemukan untuk UUID: " + cleaned, 404);
        }
    }

    // ================================================================
    // 1. GET RIWAYAT PENDAFTARAN USER
    // ================================================================
    @GET
    @Path("/riwayat")
    @Produces(MediaType.APPLICATION_JSON)
    public Response riwayatPendaftaran(@HeaderParam("userId") String userIdStr) {
        try {
            Long userId = resolveUserId(userIdStr);

            List<PendaftaranMudik> list = PendaftaranMudik.list(
                    "user.user_id = ?1 ORDER BY created_at DESC", userId);

            List<Map<String, Object>> result = new ArrayList<>();
            for (PendaftaranMudik p : list) {
                Map<String, Object> map = new HashMap<>();
                map.put("pendaftaran_id",    p.pendaftaran_id);
                map.put("uuid",              p.uuid != null ? p.uuid : "");
                map.put("kode_booking",      p.kode_booking != null ? p.kode_booking : "-");
                map.put("status_pendaftaran",p.status_pendaftaran != null ? p.status_pendaftaran : "UNKNOWN");
                map.put("nama_peserta",      p.nama_peserta != null ? p.nama_peserta : "");
                map.put("nik_peserta",       p.nik_peserta  != null ? p.nik_peserta  : "");
                map.put("alasan_tolak",      p.alasan_tolak != null ? p.alasan_tolak : "-");
                // Kirim tanggal_lahir dan kategori supaya frontend bisa tampilkan dengan benar
                map.put("tanggal_lahir",     p.tanggal_lahir != null ? p.tanggal_lahir.toString() : null);
                map.put("kategori_penumpang",p.kategori_penumpang != null ? p.kategori_penumpang : "DEWASA");
                map.put("jenis_kelamin",     p.jenis_kelamin != null ? p.jenis_kelamin : "");

                if (p.rute != null) {
                    map.put("tujuan",                p.rute.tujuan);
                    map.put("tanggal_keberangkatan", p.rute.getFormattedDate());
                } else {
                    map.put("tujuan",                "-");
                    map.put("tanggal_keberangkatan", "-");
                }

                map.put("nama_bus", p.kendaraan != null ? p.kendaraan.nama_armada : "Menunggu Plotting");

                if (p.foto_identitas_path != null && !p.foto_identitas_path.isBlank()) {
                    map.put("foto_bukti", "/uploads/" + new java.io.File(p.foto_identitas_path).getName());
                } else {
                    map.put("foto_bukti", null);
                }

                result.add(map);
            }

            return Response.ok(result).build();
        } catch (Exception e) {
            return Response.status(400).entity(Map.of("error", e.getMessage())).build();
        }
    }

    // ================================================================
    // 2. POST DAFTAR BATCH (multipart)
    // ================================================================
    @POST
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Produces(MediaType.APPLICATION_JSON)
    public Response daftarBatch(
            @HeaderParam("userId") String userIdStr,
            @QueryParam("rute_id") Long ruteId,
            PendaftaranMultipartForm form) {
        try {
            Long userId = resolveUserId(userIdStr);

            if (ruteId == null)
                return Response.status(400).entity(Map.of("error", "Pilih rute!")).build();
            if (form.nama_peserta == null || form.nama_peserta.isEmpty())
                return Response.status(400).entity(Map.of("error", "Peserta kosong!")).build();

            User user = User.findById(userId);
            Rute rute = Rute.findById(ruteId);
            if (user == null || rute == null)
                return Response.status(404).entity(Map.of("error", "Data tidak valid")).build();

            pendaftaranService.prosesPendaftaranWeb(user, rute, form);

            return Response.ok(Map.of(
                    "status", "BERHASIL",
                    "pesan",  form.nama_peserta.size() + " peserta terdaftar!"
            )).build();
        } catch (Exception e) {
            return Response.status(400).entity(Map.of("error", e.getMessage())).build();
        }
    }

    // ================================================================
    // 3. PUT KONFIRMASI KEHADIRAN (H-3)
    // ================================================================
    @PUT
    @Path("/konfirmasi-kehadiran/{userId}")
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    public Response konfirmasiKehadiran(
            @PathParam("userId") String userIdStr,
            Map<String, List<Long>> body) {
        try {
            Long userId = resolveUserId(userIdStr);

            List<Long> ids = body.get("ids_konfirmasi");
            if (ids == null)
                return Response.status(400).entity(Map.of("error", "Data 'ids_konfirmasi' tidak ditemukan")).build();

            String pesan = pendaftaranService.prosesKonfirmasi(userId, ids);
            return Response.ok(Map.of("status", "BERHASIL", "message", pesan)).build();
        } catch (Exception e) {
            e.printStackTrace();
            return Response.status(400).entity(Map.of("error", e.getMessage())).build();
        }
    }

    // ================================================================
    // 4. PUT PERBAIKI DATA (user edit setelah ditolak)
    // ================================================================
    @PUT
    @Path("/perbaiki-data/{pendaftaranId}")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Produces(MediaType.APPLICATION_JSON)
    public Response perbaikiData(
            @PathParam("pendaftaranId") Long pendaftaranId,
            @HeaderParam("userId") String userIdStr,
            PendaftaranMultipartForm form) {
        try {
            Long userId = resolveUserId(userIdStr);

            pendaftaranService.editPendaftaran(userId, pendaftaranId, form);

            return Response.ok(Map.of(
                    "status", "BERHASIL",
                    "pesan",  "Data berhasil diperbaiki. Menunggu Verifikasi."
            )).build();
        } catch (Exception e) {
            return Response.status(400).entity(Map.of("error", e.getMessage())).build();
        }
    }
}