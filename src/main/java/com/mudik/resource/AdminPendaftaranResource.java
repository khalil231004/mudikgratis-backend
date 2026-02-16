package com.mudik.resource;

import com.mudik.service.PendaftaranService;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.Map;

@Path("/api/admin") // Route khusus admin
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class AdminPendaftaranResource {

    @Inject
    PendaftaranService pendaftaranService;

    // ENDPOINT VERIFIKASI SEKELUARGA (BATCH) - POIN 3 & MAHARAJA
    @PUT
    @Path("/verifikasi-keluarga/{userId}")
    public Response verifKeluarga(@PathParam("userId") Long userId, Map<String, String> body) {
        try {
            String status = body.get("status");
            String alasan = body.get("alasan_tolak");

            pendaftaranService.updateStatusKeluarga(userId, status, alasan);

            return Response.ok(Map.of("status", "SUKSES", "pesan", "Status keluarga berhasil diperbarui")).build();
        } catch (Exception e) {
            // Poin 5: Balikin pesan error yang jelas buat UI
            return Response.status(400).entity(Map.of("error", e.getMessage())).build();
        }
    }
}