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

    // --- CREATE RUTE (Buat Admin) ---
    @POST
    @Transactional
    public Response createRute(Rute ruteBaru) {
        // 1. Validasi Input
        if (ruteBaru.kuota_total == null || ruteBaru.kuota_total <= 0) {
            return Response.status(400).entity(Map.of("error", "Kuota Total wajib diisi dan harus > 0")).build();
        }
        if (ruteBaru.asal == null || ruteBaru.tujuan == null) {
            return Response.status(400).entity(Map.of("error", "Asal dan Tujuan wajib diisi")).build();
        }

        // 2. Logic Kunci: Pas awal dibuat, sisa = total
        ruteBaru.kuota_tersisa = ruteBaru.kuota_total;

        // 3. Pastikan nama bus/plat ada default kalau kosong (opsional)
        if (ruteBaru.nama_bus == null) ruteBaru.nama_bus = "Armada Dishub";
        if (ruteBaru.plat_nomor == null) ruteBaru.plat_nomor = "Menyusul";

        ruteBaru.persist();

        return Response.status(201).entity(Map.of(
                "status", "BERHASIL",
                "message", "Rute ke " + ruteBaru.tujuan + " siap! Kuota: " + ruteBaru.kuota_total
        )).build();
    }

    // --- LIST RUTE (Buat Frontend) ---
    @GET
    public Response getAllRute() {
        // Ambil semua rute, urutkan dari yang tanggalnya paling dekat
        List<Rute> listRute = Rute.list("ORDER BY tanggal_keberangkatan ASC");

        List<Map<String, Object>> hasil = listRute.stream().map(r -> {
            Map<String, Object> map = new HashMap<>();
            map.put("rute_id", r.rute_id);
            map.put("asal", r.asal);
            map.put("tujuan", r.tujuan);

            // Info Kuota buat Frontend
            map.put("kuota_total", r.kuota_total);
            map.put("kuota_tersisa", r.kuota_tersisa);

            // Logic status: Kalau sisa 0 -> PENUH
            boolean isPenuh = (r.kuota_tersisa != null && r.kuota_tersisa <= 0);
            map.put("status_seat", isPenuh ? "HABIS" : "TERSEDIA");

            map.put("nama_bus", r.nama_bus);
            map.put("plat_nomor", r.plat_nomor);
            map.put("waktu_berangkat", r.getFormattedDate());

            return map;
        }).collect(Collectors.toList());

        return Response.ok(hasil).build();
    }

    // Tambahan: Delete Rute (Kalau admin salah input)
    @DELETE
    @Path("/{id}")
    @Transactional
    public Response deleteRute(@PathParam("id") Long id) {
        boolean deleted = Rute.deleteById(id);
        if (deleted) {
            return Response.ok(Map.of("status", "Rute dihapus")).build();
        } else {
            return Response.status(404).build();
        }
    }
}