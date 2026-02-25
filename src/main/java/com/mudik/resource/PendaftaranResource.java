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

    // DTO Wrapper
    public static class PendaftaranMultipartForm {
        @RestForm("nama_peserta") public List<String> nama_peserta;
        @RestForm("nik_peserta") public List<String> nik_peserta;
        @RestForm("jenis_kelamin") public List<String> jenis_kelamin;
        @RestForm("tanggal_lahir") public List<String> tanggal_lahir;
        @RestForm("jenis_identitas") public List<String> jenis_identitas;
        @RestForm("alamat_rumah") public List<String> alamat_rumah;
        @RestForm("no_hp_peserta") public List<String> no_hp_peserta;
        @RestForm("fotoBukti") public List<FileUpload> fotoBukti;
    }

    // ==========================================
    // 🚀 HELPER SAKTI: RESOLVE USER ID (FIX 404)
    // ==========================================
    private Long resolveUserId(String idStr) {
        if (idStr == null || idStr.isEmpty() || "undefined".equals(idStr)) {
            throw new WebApplicationException("User ID tidak valid/kosong", 400);
        }

        // 🚀 FIX: Kalau header duplikat dikirim (misal "30, 30"), ambil nilai pertama saja
        String cleaned = idStr.split(",")[0].trim().replace("\"", "");

        if (cleaned.isEmpty() || "undefined".equals(cleaned)) {
            throw new WebApplicationException("User ID tidak valid/kosong", 400);
        }

        try {
            // Cek apakah ini Angka (ID lama)
            return Long.parseLong(cleaned);
        } catch (NumberFormatException e) {
            // Kalau bukan angka, berarti UUID. Cari user berdasarkan UUID Pendaftaran
            PendaftaranMudik p = PendaftaranMudik.find("uuid", cleaned).firstResult();
            if (p != null) {
                return p.user.user_id;
            }

            throw new WebApplicationException("Data User tidak ditemukan untuk UUID: " + cleaned, 404);
        }
    }

    // 1. GET RIWAYAT (FIX: Terima String, Convert di dalam)
    @GET
    @Path("/riwayat")
    @Produces(MediaType.APPLICATION_JSON)
    public Response riwayatPendaftaran(@HeaderParam("userId") String userIdStr) {
        try {
            Long userId = resolveUserId(userIdStr); // 🚀 Auto-convert

            List<PendaftaranMudik> list = PendaftaranMudik.list("user.user_id = ?1 ORDER BY created_at DESC", userId);
            List<Map<String, Object>> result = new ArrayList<>();

            for (PendaftaranMudik p : list) {
                Map<String, Object> map = new HashMap<>();
                map.put("pendaftaran_id", p.pendaftaran_id);
                map.put("uuid", p.uuid); // Penting buat frontend
                map.put("kode_booking", p.kode_booking != null ? p.kode_booking : "-");
                map.put("status_pendaftaran", p.status_pendaftaran);
                map.put("nama_peserta", p.nama_peserta);
                map.put("nik_peserta", p.nik_peserta);
                map.put("alasan_tolak", p.alasan_tolak != null ? p.alasan_tolak : "-");

                if (p.rute != null) {
                    map.put("tujuan", p.rute.tujuan);
                    map.put("tanggal_keberangkatan", p.rute.getFormattedDate());
                } else { map.put("tujuan", "-"); }

                map.put("nama_bus", (p.kendaraan != null) ? p.kendaraan.nama_armada : "Menunggu Plotting");
                result.add(map);
            }
            return Response.ok(result).build();
        } catch (Exception e) {
            return Response.status(400).entity(Map.of("error", e.getMessage())).build();
        }
    }

    // 2. POST DAFTAR (FIX: Terima String)
    @POST
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Produces(MediaType.APPLICATION_JSON)
    public Response daftarBatch(@HeaderParam("userId") String userIdStr, @QueryParam("rute_id") Long ruteId, PendaftaranMultipartForm form) {
        try {
            Long userId = resolveUserId(userIdStr); // 🚀 Auto-convert

            if (ruteId == null) return Response.status(400).entity(Map.of("error", "Pilih rute!")).build();
            if (form.nama_peserta == null || form.nama_peserta.isEmpty()) return Response.status(400).entity(Map.of("error", "Peserta kosong!")).build();

            User user = User.findById(userId);
            Rute rute = Rute.findById(ruteId);
            if (user == null || rute == null) return Response.status(404).entity(Map.of("error", "Data tidak valid")).build();

            pendaftaranService.prosesPendaftaranWeb(user, rute, form);

            return Response.ok(Map.of("status", "BERHASIL", "pesan", form.nama_peserta.size() + " peserta terdaftar!")).build();
        } catch (Exception e) {
            return Response.status(400).entity(Map.of("error", e.getMessage())).build();
        }
    }

    // 3. PUT KONFIRMASI (FIX: PathParam String userIdStr -> Long)
    @PUT
    @Path("/konfirmasi-kehadiran/{userId}")
    public Response konfirmasiKehadiran(@PathParam("userId") String userIdStr, Map<String, List<Long>> body) {
        try {
            // 🚀 INI FIX UTAMANYA: Terima String -> Cari ID Asli -> Proses
            Long userId = resolveUserId(userIdStr);

            List<Long> ids = body.get("ids_konfirmasi");
            if (ids == null) {
                return Response.status(400).entity(Map.of("error", "Data 'ids_konfirmasi' tidak ditemukan")).build();
            }

            String pesan = pendaftaranService.prosesKonfirmasi(userId, ids);
            return Response.ok(Map.of("status", "BERHASIL", "message", pesan)).build();
        } catch (Exception e) {
            e.printStackTrace();
            return Response.status(400).entity(Map.of("error", e.getMessage())).build();
        }
    }

    // 4. PUT PERBAIKI DATA (FIX: Header String)
    @PUT
    @Path("/perbaiki-data/{pendaftaranId}")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    public Response perbaikiData(@PathParam("pendaftaranId") Long pendaftaranId,
                                 @HeaderParam("userId") String userIdStr,
                                 PendaftaranMultipartForm form) {
        try {
            Long userId = resolveUserId(userIdStr); // 🚀 Auto-convert

            pendaftaranService.editPendaftaran(userId, pendaftaranId, form);

            return Response.ok(Map.of(
                    "status", "BERHASIL",
                    "pesan", "Data berhasil diperbaiki. Menunggu Verifikasi."
            )).build();
        } catch (Exception e) {
            return Response.status(400).entity(Map.of("error", e.getMessage())).build();
        }
    }

    // ==========================================================
    // 5. GET PAGINATED UNTUK ADMIN (ENDPOINT BARU)
    // ==========================================================
    @GET
    @Path("/admin/paginated")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getPendaftarPaginated(
            @QueryParam("page") Integer page,
            @QueryParam("limit") Integer limit,
            @QueryParam("search") String search,
            @QueryParam("rute") String rute,
            @QueryParam("status") String status) {
        try {
            // Berikan default value jika null
            int pageNum = (page != null && page > 0) ? page : 1;
            int limitNum = (limit != null && limit > 0) ? limit : 50;

            Map<String, Object> result = pendaftaranService.getPendaftarAdminPaginated(pageNum, limitNum, search, rute, status);
            return Response.ok(result).build();
        } catch (Exception e) {
            e.printStackTrace();
            return Response.status(400).entity(Map.of("error", e.getMessage())).build();
        }
    }
}