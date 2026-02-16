package com.mudik.resource;

import jakarta.inject.Singleton;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.config.inject.ConfigProperty; // 🔥 WAJIB IMPORT INI
import org.jboss.resteasy.reactive.RestForm;
import org.jboss.resteasy.reactive.multipart.FileUpload;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.UUID;

@Path("/uploads") // Akses via: https://api.domain.com/uploads/namafile.jpg
@Singleton
public class UploadResource {

    // ❌ HAPUS YANG HARDCODE INI:
    // private static final String UPLOAD_DIR = "/opt/mudik-v2/uploads/";

    // ✅ GANTI JADI INI (BACA CONFIG):
    @ConfigProperty(name = "quarkus.http.body.uploads-directory")
    String uploadDir;

    @POST
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Produces(MediaType.APPLICATION_JSON)
    public Response uploadFile(@RestForm("file") FileUpload file) {
        try {
            if (file == null || file.fileName() == null) {
                return Response.status(400).entity(Map.of("error", "File kosong")).build();
            }

            // Pake variable uploadDir dari config
            File directory = new File(uploadDir);
            if (!directory.exists()) {
                directory.mkdirs(); // Buat folder otomatis kalau belum ada
            }

            String originalName = file.fileName();
            String ext = originalName.contains(".") ? originalName.substring(originalName.lastIndexOf(".")) : ".jpg";
            String newFileName = UUID.randomUUID().toString() + ext;

            // Gabungin Path (Aman buat Windows & Linux)
            java.nio.file.Path targetPath = Paths.get(uploadDir, newFileName);
            Files.move(file.uploadedFile(), targetPath, StandardCopyOption.REPLACE_EXISTING);

            return Response.ok(Map.of(
                    "status", "SUKSES",
                    "saved_path", "uploads/" + newFileName // Path relatif buat DB (tetap 'uploads/' biar frontend gak bingung)
            )).build();

        } catch (IOException e) {
            e.printStackTrace();
            return Response.serverError().entity(Map.of("error", e.getMessage())).build();
        }
    }

    @GET
    @Path("/{fileName}")
    public Response getFile(@PathParam("fileName") String fileName) throws IOException {
        // Security check (Directory Traversal Attack)
        if (fileName.contains("..") || fileName.contains("/") || fileName.contains("\\")) {
            return Response.status(400).build();
        }

        // Pake variable uploadDir dari config
        File file = new File(uploadDir, fileName);

        if (!file.exists()) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        String contentType = Files.probeContentType(file.toPath());
        if (contentType == null) contentType = "application/octet-stream";

        return Response.ok(file)
                .type(contentType)
                .header("Content-Disposition", "inline; filename=\"" + file.getName() + "\"")
                .build();
    }
}