package com.mudik.resource;

import com.mudik.model.Kendaraan;
import com.mudik.model.Rute;
import jakarta.annotation.security.RolesAllowed;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.List;
import java.util.Map;

@Path("/api/admin/kendaraan")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RolesAllowed("ADMIN")
public class KendaraanResource {

    // --- 1. TAMBAH BUS BARU KE RUTE ---
    @POST
    @Transactional
    public Response tambahKendaraan(@QueryParam("rute_id") Long ruteId, Kendaraan kendaraan) {
        Rute rute = Rute.findById(ruteId);
        if (rute == null) return Response.status(404).entity(Map.of("error", "Rute tidak ditemukan")).build();

        // Validasi
        if (kendaraan.nama_armada == null || kendaraan.kapasitas_total <= 0) {
            return Response.status(400).entity(Map.of("error", "Nama armada & kapasitas wajib diisi")).build();
        }

        kendaraan.rute = rute;
        kendaraan.terisi = 0;
        kendaraan.persist();

        // UPDATE KUOTA TOTAL RUTE
        if (rute.kuota_total == null) rute.kuota_total = 0;
        rute.kuota_total += kendaraan.kapasitas_total;
        rute.persist();

        return Response.ok(Map.of(
                "status", "BERHASIL",
                "message", "Bus " + kendaraan.nama_armada + " ditambahkan. Kuota Rute bertambah."
        )).build();
    }

    // --- 2. LIHAT DAFTAR BUS (FIXED: BISA TANPA RUTE_ID) ---
    @GET
    public Response getKendaraan(@QueryParam("rute_id") Long ruteId) {
        List<Kendaraan> list;

        if (ruteId != null) {
            // Kalau ada parameter, filter by rute
            list = Kendaraan.list("rute.rute_id", ruteId);
        } else {
            // 🔥 FIX: Kalau kosong, ambil SEMUA bus (biar Frontend gak error 400)
            list = Kendaraan.listAll();
        }

        return Response.ok(list).build();
    }

    // --- 3. HAPUS BUS ---
    @DELETE
    @Path("/{id}")
    @Transactional
    public Response hapusKendaraan(@PathParam("id") Long id) {
        Kendaraan k = Kendaraan.findById(id);
        if (k == null) return Response.status(404).build();

        // Kurangi Kuota Rute sebelum dihapus
        if (k.rute != null) {
            k.rute.kuota_total -= k.kapasitas_total;
            if (k.rute.kuota_total < 0) k.rute.kuota_total = 0;
        }

        k.delete();
        return Response.ok(Map.of("status", "BERHASIL", "message", "Bus dihapus")).build();
    }
}