package com.mudik.resource;

import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Response;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

@Path("/uploads")
public class FileResource {

    // Pastikan path ini SAMA PERSIS dengan yang ada di PendaftaranResource
    private static final String UPLOAD_DIR = "./uploads/";


    @GET
    @Path("/{fileName}")
    public Response getFile(@PathParam("fileName") String fileName) throws IOException {

        File file = new File(UPLOAD_DIR + fileName);

        // 1. Cek apakah file ada?
        if (!file.exists()) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        // 2. DETEKSI TIPE FILE OTOMATIS (MIME TYPE)
        // Ini kuncinya! Biar browser tau ini JPG, PNG, atau PDF
        String contentType = Files.probeContentType(file.toPath());

        // Jaga-jaga kalau tidak terdeteksi, kita anggap file download biasa
        if (contentType == null) {
            contentType = "application/octet-stream";
        }

        // 3. Kirim dengan Label Tipe yang Benar
        return Response.ok(file)
                .header("Content-Disposition", "inline; filename=\"" + file.getName() + "\"")
                .type(contentType) // <--- INI OBATNYA!
                .build();
    }
}