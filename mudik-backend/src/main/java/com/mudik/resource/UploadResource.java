package com.mudik.resource;

import jakarta.inject.Singleton;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.jboss.resteasy.reactive.RestForm;
import org.jboss.resteasy.reactive.multipart.FileUpload;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.UUID;

// PENTING: Ini alamat akses dari browser -> localhost:8080/uploads
@Path("/uploads")
@Singleton
public class UploadResource {

    // --- BAGIAN INI SUDAH DISESUAIKAN DENGAN PATH LAPTOP MUHAM ---
    // Di Java, tanda '\' harus ditulis ganda menjadi '\\'
    // Jangan lupa akhiri dengan '\\' juga
    private static final String UPLOAD_DIR = "./uploads/";

    // 1. FITUR UPLOAD
    @POST
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Produces(MediaType.APPLICATION_JSON)
    public Response uploadFile(@RestForm("file") FileUpload file) {
        try {
            if (file == null || file.fileName() == null) {
                return Response.status(400).entity(Map.of("error", "File kosong")).build();
            }

            // Cek folder, kalau belum ada kita buatkan otomatis
            File directory = new File(UPLOAD_DIR);
            if (!directory.exists()) {
                directory.mkdirs();
                System.out.println("📁 Folder baru dibuat di: " + directory.getAbsolutePath());
            }

            String originalName = file.fileName();
            String ext = originalName.contains(".") ? originalName.substring(originalName.lastIndexOf(".")) : ".jpg";

            // Nama file baru (UUID) biar unik
            String newFileName = UUID.randomUUID().toString() + ext;

            // Proses Simpan ke C:\Users\muham\...
            java.nio.file.Path targetPath = Paths.get(UPLOAD_DIR + newFileName);
            Files.move(file.uploadedFile(), targetPath, StandardCopyOption.REPLACE_EXISTING);

            System.out.println("✅ Upload Berhasil: " + targetPath.toString());

            // Return ke Frontend
            // Kita kembalikan path relatif saja biar database bersih
            return Response.ok(Map.of(
                    "status", "SUKSES",
                    "saved_path", "uploads/" + newFileName
            )).build();

        } catch (IOException e) {
            e.printStackTrace();
            return Response.serverError().entity(Map.of("error", e.getMessage())).build();
        }
    }

    // 2. FITUR VIEW GAMBAR (Supaya muncul di HTML)
    @GET
    @Path("/{fileName}")
    public Response getFile(@PathParam("fileName") String fileName) throws IOException {

        // Keamanan: Cegah akses folder lain (directory traversal)
        if (fileName.contains("..") || fileName.contains("/") || fileName.contains("\\")) {
            return Response.status(400).build();
        }

        // Ambil file dari C:\Users\muham\...
        File file = new File(UPLOAD_DIR + fileName);

        // Debugging di Console (Cek tab Output Netbeans kalau gambar masih 404)
        System.out.println("🔍 Request Gambar: " + file.getAbsolutePath());

        if (!file.exists()) {
            System.out.println("❌ Gambar Tidak Ditemukan!");
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        // Cek tipe file (jpg/png)
        String contentType = Files.probeContentType(file.toPath());
        if (contentType == null) contentType = "application/octet-stream";

        return Response.ok(file)
                .type(contentType)
                .header("Content-Disposition", "inline; filename=\"" + file.getName() + "\"")
                .build();
    }
}