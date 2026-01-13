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
import java.util.Map;
import java.util.UUID;

@Path("/api/pendaftaran")
public class PendaftaranResource {

    @Inject
    BotPendaftaranService botService;
    private static final String UPLOAD_DIR = "uploads/";

    @POST
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Produces(MediaType.APPLICATION_JSON)
    public Response daftar(
            @HeaderParam("userId") Long userId,
            @RestForm("rute_id") Long ruteId,
            @RestForm("nik_peserta") String nikPeserta,
            @RestForm("nama_peserta") String namaPeserta,
            @RestForm("hubungan_keluarga") String hubunganKeluarga,
            @RestForm("foto_ktp") FileUpload fileKtp,
            @RestForm("foto_kk") FileUpload fileKk,
            @RestForm("foto_barang") FileUpload fileBarang // INPUT BARU
    ) {
        try {
            if (userId == null || ruteId == null || nikPeserta == null) {
                return Response.status(400).entity(Map.of("error", "Data wajib tidak boleh kosong!")).build();
            }

            // Simpan File ke Folder
            String pathKtp = simpanFile(fileKtp, "KTP-" + nikPeserta);
            String pathKk = simpanFile(fileKk, "KK-" + nikPeserta);

            // Simpan Foto Barang (Kalau user upload)
            String pathBarang;
            if (fileBarang != null && fileBarang.fileName() != null) {
                pathBarang = simpanFile(fileBarang, "BARANG-" + nikPeserta);
            } else {
                // Bisa dikasih default image atau biarkan null
                pathBarang = "uploads/no-image.jpg";
            }

            // Panggil Service
            PendaftaranMudik hasil = botService.prosesPendaftaran(
                    userId, ruteId, nikPeserta, namaPeserta,
                    pathKtp, pathKk, pathBarang, hubunganKeluarga
            );

            return Response.ok(Map.of(
                    "status", "BERHASIL",
                    "pesan", "Pendaftaran sukses! Kode Barang: " + hasil.kode_token_barang,
                    "data", hasil
            )).build();

        } catch (IllegalArgumentException e) {
            return Response.status(400).entity(Map.of("error", e.getMessage())).build();
        } catch (Exception e) {
            e.printStackTrace();
            return Response.serverError().entity(Map.of("error", e.getMessage())).build();
        }
    }

    private String simpanFile(FileUpload fileUpload, String prefixName) throws IOException {
        if (fileUpload == null || fileUpload.fileName() == null)
            throw new IllegalArgumentException("File wajib diupload!");

        File folder = new File(UPLOAD_DIR);
        if (!folder.exists()) folder.mkdirs();

        String originalName = fileUpload.fileName();
        String extension = originalName.substring(originalName.lastIndexOf("."));
        String newFileName = prefixName + "-" + UUID.randomUUID().toString().substring(0, 8) + extension;

        File destination = new File(folder, newFileName);
        Files.copy(fileUpload.filePath(), destination.toPath(), StandardCopyOption.REPLACE_EXISTING);

        return destination.getPath();
    }
}