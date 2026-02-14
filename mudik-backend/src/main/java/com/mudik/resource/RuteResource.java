package com.mudik.resource;

import com.mudik.model.Rute;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.stream.Collectors;

@Path("/api/rute")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class RuteResource {

    // --- CREATE RUTE (Hanya Asal, Tujuan, Tanggal) ---
    @POST
    @Transactional
    public Response createRute(Rute ruteBaru) {
        if (ruteBaru.asal == null || ruteBaru.tujuan == null) {
            return Response.status(400).entity(Map.of("error", "Asal dan Tujuan wajib diisi")).build();
        }

        // Logic Triple Kuota: Awal dibuat pasti 0 (Belum ada bus)
        // Nanti Admin isi bus lewat KendaraanResource -> Kuota nambah otomatis
        ruteBaru.kuota_total = 0;
        ruteBaru.kuota_terisi = 0;
        ruteBaru.kuota_fix = 0;

        ruteBaru.persist();

        return Response.status(201).entity(Map.of(
                "status", "BERHASIL",
                "message", "Rute ke " + ruteBaru.tujuan + " dibuat. Silakan tambah kendaraan."
        )).build();
    }

    // --- LIST RUTE (Frontend) ---
    @GET
    public Response getAllRute() {
        List<Rute> listRute = Rute.list("ORDER BY tanggal_keberangkatan ASC");

        List<Map<String, Object>> hasil = listRute.stream().map(r -> {
            Map<String, Object> map = new HashMap<>();
            map.put("rute_id", r.rute_id);
            map.put("asal", r.asal);
            map.put("tujuan", r.tujuan);

            // Info Kuota Realtime
            map.put("kuota_total", r.kuota_total);
            map.put("kuota_tersisa", r.getSisaKuota()); // Pakai Helper

            boolean isPenuh = (r.getSisaKuota() <= 0);
            map.put("status_seat", isPenuh ? "HABIS" : "TERSEDIA");

            // Tidak ada nama bus di sini, karena bus bisa banyak
            map.put("waktu_berangkat", r.getFormattedDate());

            return map;
        }).collect(Collectors.toList());

        return Response.ok(hasil).build();
    }

    @DELETE
    @Path("/{id}")
    @Transactional
    public Response deleteRute(@PathParam("id") Long id) {
        boolean deleted = Rute.deleteById(id);
        return deleted ? Response.ok().build() : Response.status(404).build();
    }
}