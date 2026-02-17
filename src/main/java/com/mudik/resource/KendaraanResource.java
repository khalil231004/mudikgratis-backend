package com.mudik.resource;

import com.mudik.model.Kendaraan;
import com.mudik.model.Rute;
import jakarta.persistence.LockModeType;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.List;
import java.util.Map;

@Path("/api/admin/kendaraan")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class KendaraanResource {

    // --- 1. TAMBAH BUS BARU (MURNI DATA BUS SAJA) ---
    @POST
    @Transactional
    public Response tambahKendaraan(@QueryParam("rute_id") Long ruteId, Kendaraan kendaraan) {
        Rute rute = Rute.findById(ruteId); // Gak perlu Lock, karena gak ubah kuota rute
        if (rute == null) return Response.status(404).entity(Map.of("error", "Rute tidak ditemukan")).build();

        if (kendaraan.nama_armada == null || kendaraan.kapasitas_total <= 0) {
            return Response.status(400).entity(Map.of("error", "Nama armada & kapasitas wajib diisi")).build();
        }

        kendaraan.rute = rute;
        kendaraan.terisi = 0;
        kendaraan.persist();


        return Response.ok(Map.of(
                "status", "BERHASIL",
                "message", "Bus " + kendaraan.nama_armada + " ditambahkan. (Kuota Rute TIDAK BERUBAH)"
        )).build();
    }

    // --- 2. LIHAT DAFTAR BUS ---
    @GET
    public Response getKendaraan(@QueryParam("rute_id") Long ruteId) {
        if (ruteId != null) {
            return Response.ok(Kendaraan.list("rute.rute_id", ruteId)).build();
        }
        return Response.ok(Kendaraan.listAll()).build();
    }

    // --- 3. HAPUS BUS ---
    @DELETE
    @Path("/{id}")
    @Transactional
    public Response hapusKendaraan(@PathParam("id") Long id) {
        Kendaraan k = Kendaraan.findById(id);
        if (k == null) return Response.status(404).entity(Map.of("error", "Bus tidak ditemukan")).build();


        k.delete();
        return Response.ok(Map.of("status", "BERHASIL", "message", "Bus dihapus. Kuota Rute Tetap.")).build();
    }
}