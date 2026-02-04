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


@Path("/uploads")
@Singleton
public class UploadResource {

    private static final String UPLOAD_DIR = "./uploads/";


    @POST
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Produces(MediaType.APPLICATION_JSON)
    public Response uploadFile(@RestForm("file") FileUpload file) {
        try {
            if (file == null || file.fileName() == null) {
                return Response.status(400).entity(Map.of("error", "File kosong")).build();
            }

            File directory = new File(UPLOAD_DIR);
            if (!directory.exists()) {
                directory.mkdirs();
                System.out.println("📁 Folder baru dibuat di: " + directory.getAbsolutePath());
            }

            String originalName = file.fileName();
            String ext = originalName.contains(".") ? originalName.substring(originalName.lastIndexOf(".")) : ".jpg";

            String newFileName = UUID.randomUUID().toString() + ext;

            java.nio.file.Path targetPath = Paths.get(UPLOAD_DIR + newFileName);
            Files.move(file.uploadedFile(), targetPath, StandardCopyOption.REPLACE_EXISTING);

            System.out.println("✅ Upload Berhasil: " + targetPath.toString());


            return Response.ok(Map.of(
                    "status", "SUKSES",
                    "saved_path", "uploads/" + newFileName
            )).build();

        } catch (IOException e) {
            e.printStackTrace();
            return Response.serverError().entity(Map.of("error", e.getMessage())).build();
        }
    }


    @GET
    @Path("/{fileName}")
    public Response getFile(@PathParam("fileName") String fileName) throws IOException {


        if (fileName.contains("..") || fileName.contains("/") || fileName.contains("\\")) {
            return Response.status(400).build();
        }


        File file = new File(UPLOAD_DIR + fileName);

        System.out.println("🔍 Request Gambar: " + file.getAbsolutePath());

        if (!file.exists()) {
            System.out.println("❌ Gambar Tidak Ditemukan!");
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