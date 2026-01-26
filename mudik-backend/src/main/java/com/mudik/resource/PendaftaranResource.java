package com.mudik.resource;

import com.mudik.model.PendaftaranMudik;
import com.mudik.service.BotPendaftaranService;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.jboss.resteasy.reactive.RestForm;
import org.jboss.resteasy.reactive.multipart.FileUpload;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Path("/api/pendaftaran")
public class PendaftaranResource {

    @Inject
    BotPendaftaranService botService;

    // Folder Upload
    private static final String UPLOAD_DIR = "uploads/";

    // --- FORM KHUSUS BIAR SWAGGER NGERTI MULTIPART ---
    public static class PendaftaranMultipartForm {
        @RestForm("nama_peserta")
        public List<String> nama_peserta;

        @RestForm("nik_peserta")
        public List<String> nik_peserta;

        @RestForm("jenis_kelamin")
        public List<String> jenis_kelamin;

        @RestForm("tanggal_lahir")
        public List<String> tanggal_lahir;

        @RestForm("jenis_identitas")
        public List<String> jenis_identitas;

        @RestForm("berat_barang")
        public List<Double> berat_barang;

        @RestForm("ukuran_barang")
        public List<String> ukuran_barang;

        @RestForm("titik_jemput")
        public List<String> titik_jemput;

        @RestForm("no_hp_peserta")
        public List<String> no_hp_peserta;

        // INI PENTING: Tipe FileUpload biar Swagger munculin tombol Upload
        @RestForm("foto_bukti")
        public List<FileUpload> fotoBukti;
    }


    @GET
    @Path("/cek/{nik}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response cekStatus(@PathParam("nik") String nik) {
        // Cari data di database berdasarkan kolom "nik_peserta"
        // Pastikan PendaftaranMudik sudah di-import
        PendaftaranMudik pendaftar = PendaftaranMudik.find("nik_peserta", nik).firstResult();

        if (pendaftar == null) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(Map.of("message", "NIK tidak ditemukan"))
                    .build();
        }

        return Response.ok(pendaftar).build();
    }

    @GET
    @Path("/riwayat")
    @Produces(MediaType.APPLICATION_JSON)
    public Response riwayatPendaftaran(@HeaderParam("userId") Long userId) {
        if (userId == null) return Response.status(400).entity(Map.of("error", "User ID wajib diisi")).build();
        return Response.ok(PendaftaranMudik.list("user.user_id = ?1 ORDER BY created_at DESC", userId)).build();
    }

    // --- ENDPOINT UTAMA (MULTIPART) ---
    @POST
    @Consumes(MediaType.MULTIPART_FORM_DATA) // <--- INI KUNCINYA BIAR SWAGGER GAK JSON
    @Produces(MediaType.APPLICATION_JSON)
    public Response daftarBatch(
            @HeaderParam("userId") Long userId,
            @QueryParam("rute_id") Long ruteId,
            PendaftaranMultipartForm form // Parameter Form
    ) {
        try {
            // 1. Validasi Dasar
            if (userId == null) return Response.status(400).entity(Map.of("error", "Header 'userId' kosong!")).build();
            if (ruteId == null) return Response.status(400).entity(Map.of("error", "Query param 'rute_id' kosong!")).build();
            if (form.nama_peserta == null || form.nama_peserta.isEmpty()) {
                return Response.status(400).entity(Map.of("error", "Form kosong!")).build();
            }

            int jumlahData = form.nama_peserta.size();

            // 2. Simpan Semua File Dulu ke Folder Uploads
            List<String> listPathBukti = new ArrayList<>();

            if (form.fotoBukti != null) {
                // Loop file yang diupload
                for (int i = 0; i < form.fotoBukti.size(); i++) {
                    FileUpload file = form.fotoBukti.get(i);
                    // Ambil NIK pasangannya biar nama file unik (Pake index yg sama)
                    String nikRef = (form.nik_peserta.size() > i) ? form.nik_peserta.get(i) : "unknown";

                    String savedPath = simpanFile(file, nikRef);
                    listPathBukti.add(savedPath);
                }
            }

            // Validasi jumlah foto vs jumlah orang
            if (listPathBukti.size() != jumlahData) {
                return Response.status(400).entity(Map.of("error", "Jumlah Foto tidak sama dengan Jumlah Peserta! (Harus upload semua)")).build();
            }

            // 3. Panggil Logic Bot Service (Simpan ke DB)
            // Handle List HP biar aman kalau null
            List<String> listHpAman = (form.no_hp_peserta != null) ? form.no_hp_peserta : new ArrayList<>();

            botService.prosesPendaftaranBatch(
                    userId, ruteId,
                    form.nama_peserta, form.nik_peserta, form.jenis_kelamin,
                    form.tanggal_lahir, form.jenis_identitas,
                    listPathBukti, // Kirim Path yg udah disimpen
                    form.berat_barang, form.ukuran_barang,
                    form.titik_jemput,
                    listHpAman
            );

            return Response.ok(Map.of(
                    "status", "BERHASIL",
                    "pesan", jumlahData + " peserta berhasil didaftarkan!"
            )).build();

        } catch (IllegalArgumentException e) {
            // Error validasi (kuota habis, nik kembar, dll)
            return Response.status(400).entity(Map.of("error", e.getMessage())).build();
        } catch (Exception e) {
            e.printStackTrace();
            return Response.serverError().entity(Map.of("error", "Gagal: " + e.getMessage())).build();
        }
    }

    // Helper: Simpan File Fisik
    private String simpanFile(FileUpload fileUpload, String nik) throws IOException {
        File folder = new File(UPLOAD_DIR);
        if (!folder.exists()) folder.mkdirs();

        String originalName = fileUpload.fileName();
        String ext = (originalName.contains(".")) ? originalName.substring(originalName.lastIndexOf(".")) : ".jpg";

        // Nama File: ktp-NIK-UUID.jpg
        String newName = "ktp-" + nik + "-" + UUID.randomUUID().toString().substring(0, 5) + ext;

        File dest = new File(folder, newName);
        Files.move(fileUpload.uploadedFile(), dest.toPath(), StandardCopyOption.REPLACE_EXISTING);

        return UPLOAD_DIR + newName;
    }
}