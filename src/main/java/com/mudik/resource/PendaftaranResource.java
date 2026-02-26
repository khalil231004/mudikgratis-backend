package com.mudik.resource;

import com.mudik.model.PendaftaranMudik;
import com.mudik.model.Rute;
import com.mudik.model.User;
import com.mudik.service.PendaftaranService;
import io.quarkus.security.Authenticated;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.jwt.JsonWebToken;
import org.jboss.resteasy.reactive.RestForm;
import org.jboss.resteasy.reactive.multipart.FileUpload;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * FIX KEAMANAN KRITIS:
 * - Hapus @HeaderParam("userId") yang tidak aman (siapapun bisa palsukan header)
 * - Gunakan @Inject JsonWebToken untuk ambil userId dari JWT yang terverifikasi
 * - Tambah @Authenticated di class level
 */
@Path("/api/pendaftaran")
@Authenticated // FIX: Wajib token untuk semua endpoint pendaftaran
@Produces(MediaType.APPLICATION_JSON)
public class PendaftaranResource {

    @Inject
    PendaftaranService pendaftaranService;

    // FIX: Inject JWT untuk baca claim user_id yang aman
    @Inject
    JsonWebToken jwt;

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
    // HELPER: Ambil userId dari JWT (aman, tidak bisa dipalsukan)
    // ================================================================
    private Long getUserIdFromJwt() {
        // Coba baca claim "id_user" yang di-set saat sign JWT di AuthResource
        Object idClaim = jwt.getClaim("id_user");
        if (idClaim != null) {
            try {
                // JWT claim bisa bertipe Integer atau Long tergantung serializer
                if (idClaim instanceof Number) {
                    return ((Number) idClaim).longValue();
                }
                return Long.parseLong(idClaim.toString());
            } catch (NumberFormatException e) {
                // fall through
            }
        }
        // Fallback: cari user berdasarkan email (subject JWT = UPN = email)
        String email = jwt.getSubject() != null ? jwt.getSubject() : jwt.getName();
        User user = User.find("email", email).firstResult();
        if (user == null) {
            throw new WebApplicationException("User tidak ditemukan untuk token ini", 401);
        }
        return user.user_id;
    }

    // ================================================================
    // 1. GET RIWAYAT PENDAFTARAN USER
    // ================================================================
    @GET
    @Path("/riwayat")
    public Response riwayatPendaftaran() {
        try {
            Long userId = getUserIdFromJwt();

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

                map.put("nama_bus",    p.kendaraan != null ? p.kendaraan.nama_armada : "Menunggu Plotting");
                map.put("plat_nomor",  p.kendaraan != null ? p.kendaraan.plat_nomor : null);
                map.put("link_konfirmasi_dikirim", p.link_konfirmasi_dikirim);

                if (p.foto_identitas_path != null && !p.foto_identitas_path.isBlank()) {
                    map.put("foto_bukti", "/uploads/" + new java.io.File(p.foto_identitas_path).getName());
                } else {
                    map.put("foto_bukti", null);
                }

                result.add(map);
            }

            return Response.ok(result).build();
        } catch (WebApplicationException wae) {
            throw wae;
        } catch (Exception e) {
            return Response.status(400).entity(Map.of("error", e.getMessage())).build();
        }
    }

    // ================================================================
    // 2. POST DAFTAR BATCH (multipart)
    // ================================================================
    @POST
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    public Response daftarBatch(
            @QueryParam("rute_id") Long ruteId,
            PendaftaranMultipartForm form) {
        try {
            Long userId = getUserIdFromJwt();

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
        } catch (WebApplicationException wae) {
            throw wae;
        } catch (Exception e) {
            return Response.status(400).entity(Map.of("error", e.getMessage())).build();
        }
    }

    // ================================================================
    // 3. PUT KONFIRMASI KEHADIRAN (H-3)
    // FIX: PathParam userId tidak perlu lagi, ambil dari JWT
    // ================================================================
    @PUT
    @Path("/konfirmasi-kehadiran")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response konfirmasiKehadiran(Map<String, List<Long>> body) {
        try {
            Long userId = getUserIdFromJwt();

            List<Long> ids = body.get("ids_konfirmasi");
            if (ids == null)
                return Response.status(400).entity(Map.of("error", "Data 'ids_konfirmasi' tidak ditemukan")).build();

            long sudahDikirim = PendaftaranMudik.count(
                    "user.user_id = ?1 AND link_konfirmasi_dikirim = true", userId);
            if (sudahDikirim == 0) {
                return Response.status(403).entity(Map.of(
                        "error", "Konfirmasi belum tersedia. Silakan tunggu admin mengirim link konfirmasi sesuai jadwal.",
                        "kode", "LINK_BELUM_DIKIRIM"
                )).build();
            }

            String pesan = pendaftaranService.prosesKonfirmasi(userId, ids);
            return Response.ok(Map.of("status", "BERHASIL", "message", pesan)).build();
        } catch (WebApplicationException wae) {
            throw wae;
        } catch (Exception e) {
            e.printStackTrace();
            return Response.status(400).entity(Map.of("error", e.getMessage())).build();
        }
    }

    // ================================================================
    // 4. PUT PERBAIKI DATA (user edit setelah ditolak)
    // FIX: Hapus @HeaderParam("userId"), ambil dari JWT
    // ================================================================
    @PUT
    @Path("/perbaiki-data/{pendaftaranId}")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    public Response perbaikiData(
            @PathParam("pendaftaranId") Long pendaftaranId,
            PendaftaranMultipartForm form) {
        try {
            Long userId = getUserIdFromJwt();

            pendaftaranService.editPendaftaran(userId, pendaftaranId, form);

            return Response.ok(Map.of(
                    "status", "BERHASIL",
                    "pesan",  "Data berhasil diperbaiki. Menunggu Verifikasi."
            )).build();
        } catch (WebApplicationException wae) {
            throw wae;
        } catch (Exception e) {
            return Response.status(400).entity(Map.of("error", e.getMessage())).build();
        }
    }
}
