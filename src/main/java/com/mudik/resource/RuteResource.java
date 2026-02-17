package com.mudik.resource;

import com.mudik.model.Rute;
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
    // 1. PUBLIC API (Untuk Pendaftar / User Dashboard)
    // ==========================================

    @GET
    public Response getAllRute() {
        List<Rute> listRute = Rute.list("ORDER BY tanggal_keberangkatan ASC");

        List<Map<String, Object>> hasil = listRute.stream().map(r -> {
            Map<String, Object> map = new HashMap<>();
            map.put("rute_id", r.rute_id);
            map.put("asal", r.asal);
            map.put("tujuan", r.tujuan);
            map.put("tanggal_raw", r.tanggal_keberangkatan);
            map.put("waktu_berangkat", r.getFormattedDate());

            // Info Kuota Public
            map.put("kuota_total", r.kuota_total != null ? r.kuota_total : 0);

            // 🔥 UPDATE AMAN: Kirim dua-duanya biar Frontend User gak bingung
            map.put("kuota_tersisa", r.getSisaKuota());
            map.put("sisa_kuota", r.getSisaKuota());

            // Status Seat
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
    // 2. ADMIN API (Full Detail Kuota Untuk AdminRute.tsx)
    // ==========================================

    @GET
    @Path("/admin")
    public Response getAllRuteAdmin() {
        List<Rute> listRute = Rute.list("ORDER BY tanggal_keberangkatan ASC");

        List<Map<String, Object>> hasil = listRute.stream().map(r -> {
            Map<String, Object> map = new HashMap<>();
            map.put("rute_id", r.rute_id);
            map.put("asal", r.asal);
            map.put("tujuan", r.tujuan);
            map.put("tanggal_raw", r.tanggal_keberangkatan);
            map.put("waktu_berangkat", r.getFormattedDate());

            // 🔥 DETAIL KUOTA LENGKAP UTK ADMIN
            map.put("kuota_total", r.kuota_total != null ? r.kuota_total : 0);
            map.put("kuota_terisi", r.kuota_terisi != null ? r.kuota_terisi : 0);
            map.put("kuota_fix", r.kuota_fix != null ? r.kuota_fix : 0);

            // 🔥 UPDATE AMAN: Kirim dua-duanya biar AdminRute.tsx pasti muncul angkanya
            map.put("sisa_kuota", r.getSisaKuota());
            map.put("kuota_tersisa", r.getSisaKuota());

            return map;
        }).collect(Collectors.toList());

        return Response.ok(hasil).build();
    }

    // ==========================================
    // 3. LOGIKA CREATE & UPDATE (MANUAL MODE)
    // ==========================================

    @POST
    @Transactional
    public Response createRute(Rute ruteBaru) {
        if (ruteBaru.asal == null || ruteBaru.tujuan == null) {
            return Response.status(400).entity(Map.of("error", "Asal dan Tujuan wajib diisi")).build();
        }

        // Logic Manual: Kalau admin kirim angka kuota, pake itu. Kalau null, 0.
        if(ruteBaru.kuota_total == null) ruteBaru.kuota_total = 0;

        // Reset counter (Aman)
        ruteBaru.kuota_terisi = 0;
        ruteBaru.kuota_fix = 0;

        ruteBaru.persist();

        return Response.status(201).entity(Map.of(
                "status", "BERHASIL",
                "message", "Rute berhasil dibuat. Total Kuota: " + ruteBaru.kuota_total
        )).build();
    }

    @PUT
    @Path("/{id}")
    @Transactional
    public Response updateRute(@PathParam("id") Long id, Rute dataBaru) {
        Rute rute = Rute.findById(id);
        if (rute == null) return Response.status(404).entity(Map.of("error", "Rute tidak ditemukan")).build();

        rute.asal = dataBaru.asal;
        rute.tujuan = dataBaru.tujuan;
        rute.tanggal_keberangkatan = dataBaru.tanggal_keberangkatan;

        // 🔥 Logic Manual Override:
        // Admin bisa ubah angka kuota total kapan saja lewat menu edit
        if(dataBaru.kuota_total != null) {
            rute.kuota_total = dataBaru.kuota_total;
        }

        rute.persist();
        return Response.ok(Map.of("status", "BERHASIL", "message", "Data rute diperbarui")).build();
    }

    @DELETE
    @Path("/{id}")
    @Transactional
    public Response deleteRute(@PathParam("id") Long id) {
        Rute rute = Rute.findById(id);
        if (rute == null) return Response.status(404).build();

        // Safety: Jangan hapus rute kalau sudah ada yang daftar
        if (rute.kuota_terisi != null && rute.kuota_terisi > 0) {
            return Response.status(400).entity(Map.of("error", "Gagal hapus! Masih ada " + rute.kuota_terisi + " pendaftar di rute ini.")).build();
        }

        rute.delete();
        return Response.ok(Map.of("status", "BERHASIL", "message", "Rute dihapus")).build();
    }
}