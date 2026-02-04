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

        if (!"DITERIMA".equalsIgnoreCase(pendaftar.status_pendaftaran)) {
            return Response.status(400).entity("Maaf, tiket hanya bisa dicetak jika status Anda DITERIMA.").build();
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