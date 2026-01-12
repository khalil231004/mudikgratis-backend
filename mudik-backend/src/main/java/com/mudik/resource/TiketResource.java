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
    @Produces("application/pdf") // Browser bakal tahu ini file PDF
    public Response downloadTiket(@PathParam("id") Long id) {

        // 1. Cari Data Pendaftar
        PendaftaranMudik pendaftar = PendaftaranMudik.findById(id);

        // 2. Validasi: Ada gak orangnya?
        if (pendaftar == null) {
            return Response.status(404).entity("Data tidak ditemukan").build();
        }

        // 3. Validasi: Udah DITERIMA belum?
        // Masa Status DITOLAK bisa cetak tiket? 😂
        if (!"DITERIMA".equalsIgnoreCase(pendaftar.status_pendaftaran)) {
            return Response.status(400).entity("Maaf, tiket hanya bisa dicetak jika status Anda DITERIMA.").build();
        }

        try {
            // 4. Generate PDF
            byte[] pdfBytes = tiketService.cetakTiket(pendaftar);

            // 5. Kirim File ke Browser
            return Response.ok(pdfBytes)
                    .header("Content-Disposition", "attachment; filename=Tiket_Mudik_" + pendaftar.nama_peserta + ".pdf")
                    .build();

        } catch (Exception e) {
            e.printStackTrace();
            return Response.serverError().entity("Gagal mencetak tiket: " + e.getMessage()).build();
        }
    }
}