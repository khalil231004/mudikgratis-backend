package com.mudik.resource;

import com.mudik.model.PendaftaranMudik;
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

            if (r.tanggal_keberangkatan != null) {
                java.time.ZoneId wib = java.time.ZoneId.of("Asia/Jakarta");
                java.time.ZonedDateTime wibTime = r.tanggal_keberangkatan.atZone(java.time.ZoneId.of("UTC")).withZoneSameInstant(wib);
                map.put("tanggal_berangkat", wibTime.toLocalDate().toString());
                map.put("jam_berangkat", String.format("%02d:%02d WIB", wibTime.getHour(), wibTime.getMinute()));
            } else {
                map.put("tanggal_berangkat", "-");
                map.put("jam_berangkat", "-");
            }

            map.put("kuota_total", r.kuota_total != null ? r.kuota_total : 0);
            map.put("kuota_tersisa", r.getSisaKuota());
            map.put("sisa_kuota", r.getSisaKuota());

            boolean isPenuh = (r.getSisaKuota() <= 0);
            map.put("status_seat", isPenuh ? "HABIS" : "TERSEDIA");

            map.put("is_portal_open", r.is_portal_open != null ? r.is_portal_open : true);

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

            if (r.tanggal_keberangkatan != null) {
                java.time.ZoneId wib = java.time.ZoneId.of("Asia/Jakarta");
                java.time.ZonedDateTime wibTime = r.tanggal_keberangkatan.atZone(java.time.ZoneId.of("UTC")).withZoneSameInstant(wib);
                map.put("tanggal_berangkat", wibTime.toLocalDate().toString());
                map.put("jam_berangkat", String.format("%02d:%02d WIB", wibTime.getHour(), wibTime.getMinute()));
            } else {
                map.put("tanggal_berangkat", "-");
                map.put("jam_berangkat", "-");
            }

            // 🔥 DETAIL KUOTA LENGKAP UTK ADMIN
            map.put("kuota_total", r.kuota_total != null ? r.kuota_total : 0);
            map.put("kuota_terisi", r.kuota_terisi != null ? r.kuota_terisi : 0); // hanya DITERIMA H-3 ke atas
            map.put("kuota_fix", r.kuota_fix != null ? r.kuota_fix : 0);
            map.put("sisa_kuota", r.getSisaKuota());
            map.put("kuota_tersisa", r.getSisaKuota());

            // ✅ TAMBAHAN: Hitung total pendaftar MENUNGGU VERIFIKASI untuk info admin
            long menunggu = PendaftaranMudik.count(
                    "rute.rute_id = ?1 AND status_pendaftaran = 'MENUNGGU VERIFIKASI'", r.rute_id);
            map.put("kuota_menunggu", menunggu); // informasi saja, tidak memakan kuota

            map.put("is_portal_open", r.is_portal_open != null ? r.is_portal_open : true);

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

        if (ruteBaru.kuota_total == null) ruteBaru.kuota_total = 0;
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

        if (dataBaru.kuota_total != null) {
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

        // ✅ REVISI: Cek pendaftar aktif dari DB langsung (bukan dari kuota_terisi)
        //    karena MENUNGGU VERIFIKASI tidak lagi memakai kuota_terisi
        long pendaftarAktif = PendaftaranMudik.count(
                "rute.rute_id = ?1 AND status_pendaftaran NOT IN ('DITOLAK', 'DIBATALKAN')", id);
        if (pendaftarAktif > 0) {
            return Response.status(400).entity(Map.of(
                    "error", "Gagal hapus! Masih ada " + pendaftarAktif + " pendaftar aktif di rute ini."
            )).build();
        }

        rute.delete();
        return Response.ok(Map.of("status", "BERHASIL", "message", "Rute dihapus")).build();
    }

    // FIX 11: Endpoint buka/tutup portal pendaftaran per rute
    @PUT
    @Path("/{id}/portal")
    @Transactional
    public Response togglePortal(@PathParam("id") Long id, Map<String, Object> body) {
        Rute rute = Rute.findById(id);
        if (rute == null) return Response.status(404).entity(Map.of("error", "Rute tidak ditemukan")).build();

        Object val = body.get("is_portal_open");
        if (val == null) return Response.status(400).entity(Map.of("error", "Field is_portal_open wajib diisi")).build();

        rute.is_portal_open = Boolean.parseBoolean(val.toString());
        rute.persist();

        String kondisi = rute.is_portal_open ? "DIBUKA" : "DITUTUP";
        return Response.ok(Map.of("status", "BERHASIL", "message", "Portal pendaftaran rute " + rute.tujuan + " berhasil " + kondisi)).build();
    }
}