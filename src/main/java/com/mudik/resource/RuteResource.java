package com.mudik.resource;

import com.mudik.model.Rute;
import jakarta.annotation.security.RolesAllowed;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Path("/api/rute")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class RuteResource {

    // ==========================================
    // 1. PUBLIC API (Untuk User & Dropdown Admin)
    // ==========================================

    @GET
    public Response getAllRute() {
        // Urutkan berdasarkan tanggal
        List<Rute> listRute = Rute.list("ORDER BY tanggal_keberangkatan ASC");

        List<Map<String, Object>> hasil = listRute.stream().map(r -> {
            Map<String, Object> map = new HashMap<>();
            map.put("rute_id", r.rute_id);
            map.put("asal", r.asal);
            map.put("tujuan", r.tujuan);

            // Raw Date untuk Form Edit Admin
            map.put("tanggal_raw", r.tanggal_keberangkatan);
            // Formatted Date untuk Tampilan Tabel/User
            map.put("waktu_berangkat", r.getFormattedDate());

            // Info Kuota Realtime
            map.put("kuota_total", r.kuota_total);
            map.put("kuota_terisi", r.kuota_terisi);
            map.put("kuota_fix", r.kuota_fix);
            map.put("kuota_tersisa", r.getSisaKuota()); // Pakai Helper

            boolean isPenuh = (r.getSisaKuota() <= 0);
            map.put("status_seat", isPenuh ? "HABIS" : "TERSEDIA");

            return map;
        }).collect(Collectors.toList());

        return Response.ok(hasil).build();
    }

    @GET
    @Path("/{id}")
    public Response getOne(@PathParam("id") Long id) {
        Rute rute = Rute.findById(id);
        if (rute == null) return Response.status(404).entity(Map.of("error", "Rute tidak ditemukan")).build();
        return Response.ok(rute).build();
    }

    // ==========================================
    // 2. ADMIN API (Create, Update, Delete)
    // ==========================================

    @POST
    @Transactional
    @RolesAllowed("ADMIN")
    public Response createRute(Rute ruteBaru) {
        if (ruteBaru.asal == null || ruteBaru.tujuan == null) {
            return Response.status(400).entity(Map.of("error", "Asal dan Tujuan wajib diisi")).build();
        }

        // Default Kuota 0 jika tidak diisi
        if(ruteBaru.kuota_total == null) ruteBaru.kuota_total = 0;
        ruteBaru.kuota_terisi = 0;
        ruteBaru.kuota_fix = 0;

        ruteBaru.persist();

        return Response.status(201).entity(Map.of(
                "status", "BERHASIL",
                "message", "Rute ke " + ruteBaru.tujuan + " berhasil dibuat."
        )).build();
    }

    @PUT
    @Path("/{id}")
    @Transactional
    @RolesAllowed("ADMIN")
    public Response updateRute(@PathParam("id") Long id, Rute dataBaru) {
        Rute rute = Rute.findById(id);
        if (rute == null) return Response.status(404).entity(Map.of("error", "Rute tidak ditemukan")).build();

        // Update Field
        rute.asal = dataBaru.asal;
        rute.tujuan = dataBaru.tujuan;
        rute.tanggal_keberangkatan = dataBaru.tanggal_keberangkatan;

        // Admin boleh manual override kuota total jika perlu
        if(dataBaru.kuota_total != null) {
            rute.kuota_total = dataBaru.kuota_total;
        }

        rute.persist();
        return Response.ok(Map.of("status", "BERHASIL", "message", "Data rute diupdate")).build();
    }

    @DELETE
    @Path("/{id}")
    @Transactional
    @RolesAllowed("ADMIN")
    public Response deleteRute(@PathParam("id") Long id) {
        boolean deleted = Rute.deleteById(id);
        if(deleted) {
            return Response.ok(Map.of("status", "BERHASIL", "message", "Rute dihapus")).build();
        } else {
            return Response.status(404).entity(Map.of("error", "Gagal hapus, data tidak ada")).build();
        }
    }
}