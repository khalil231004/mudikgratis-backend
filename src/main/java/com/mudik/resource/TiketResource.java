package com.mudik.resource;

import com.mudik.model.PendaftaranMudik;
import com.mudik.service.TiketService;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Response;

@Path("/api/tiket")
public class TiketResource {

    @Inject
    TiketService tiketService;

    @GET
    @Path("/download/{id}")
    @Produces("application/pdf")
    public Response downloadTiket(@PathParam("id") Long id) {

        PendaftaranMudik pendaftar = PendaftaranMudik.findById(id);
        if (pendaftar == null) {
            return Response.status(404).entity("Data pendaftaran tidak ditemukan").build();
        }

        // FIX 2: Tiket bisa didownload saat sudah plotting (status TERVERIFIKASI/ SIAP BERANGKAT atau TERKONFIRMASI)
        boolean statusValid = "TERKONFIRMASI".equalsIgnoreCase(pendaftar.status_pendaftaran)
                || "TERVERIFIKASI/ SIAP BERANGKAT".equalsIgnoreCase(pendaftar.status_pendaftaran);

        if (!statusValid) {
            return Response.status(400).entity("Tiket belum tersedia. Status pendaftaran Anda belum siap untuk download tiket.").build();
        }

        // 2. CEK BUS / KENDARAAN (Ini kuncinya!)
        // Kalau admin belum plotting (kendaraan masih kosong), tolak.
        if (pendaftar.kendaraan == null) {
            return Response.status(400).entity("Mohon tunggu. Admin sedang mengatur pembagian kursi Bus (Plotting). Tiket bisa diunduh setelah Bus ditentukan.").build();
        }

        try {
            byte[] pdfBytes = tiketService.cetakTiket(pendaftar);

            return Response.ok(pdfBytes)
                    .header("Content-Disposition", "attachment; filename=Tiket_Mudik_" + pendaftar.nama_peserta.replace(" ", "_") + ".pdf")
                    .build();

        } catch (Exception e) {
            e.printStackTrace();
            return Response.serverError().entity("Gagal mencetak tiket: " + e.getMessage()).build();
        }
    }
}