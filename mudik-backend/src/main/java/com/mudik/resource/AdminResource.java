package com.mudik.resource;

import com.mudik.model.PendaftaranMudik;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.List;
import java.util.Map;

@Path("/api/admin")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class AdminResource {

    public enum PilihanStatus {
        DITERIMA,
        DITOLAK,
        BATAL
    }

    @GET
    @Path("/pendaftar")
    public List<PendaftaranMudik> getAllPendaftar() {
        return PendaftaranMudik.list("ORDER BY created_at DESC");
    }

    @PUT
    @Path("/verifikasi/{id}")
    @Transactional
    public Response verifikasiManual(@PathParam("id") Long id, Map<String, PilihanStatus> body) {

        PendaftaranMudik data = PendaftaranMudik.findById(id);
        if (data == null) return Response.status(404).entity(Map.of("error", "Data tidak ditemukan")).build();

        PilihanStatus pilihan = body.get("keputusan");
        if (pilihan == null) return Response.status(400).entity(Map.of("error", "Wajib pilih keputusan!")).build();

        String statusBaru = pilihan.toString();
        String statusLama = (data.status_pendaftaran != null) ? data.status_pendaftaran.toUpperCase() : "UNKNOWN";

        if (statusLama.equals(statusBaru)) {
            return Response.ok(Map.of("status", "TETAP", "pesan", "Status tidak berubah.")).build();
        }

        System.out.println(">>> UBAH STATUS: " + statusLama + " -> " + statusBaru);

        boolean isBaruGagal = "DITOLAK".equals(statusBaru) || "BATAL".equals(statusBaru);
        boolean isLamaDiterima = "DITERIMA".equals(statusLama);

        if (isBaruGagal && isLamaDiterima) {
            if (data.rute != null) {
                data.rute.kuota_tersisa += 1; // Balikin Tiket
                data.rute.persist();
                System.out.println(">>> KUOTA DIKEMBALIKAN (+1)");
            }
        } else if ("DITERIMA".equals(statusBaru)) {
            if (data.rute != null) {
                if (data.rute.kuota_tersisa <= 0) {
                    // Kalau ternyata pas Admin mau klik Terima, eh kursinya habis diserobot admin lain:
                    return Response.status(400).entity(Map.of(
                            "status", "GAGAL",
                            "error", "Gagal Verifikasi! Kuota rute ini BARU SAJA HABIS (0)."
                    )).build();
                }
                data.rute.kuota_tersisa -= 1; // Potong Tiket Disini!
                data.rute.persist();
                System.out.println(">>> KUOTA DIPOTONG (-1)");
            }
        }

        data.status_pendaftaran = statusBaru;
        data.persist();

        return Response.ok(Map.of(
                "status", "SUKSES",
                "pesan", "Status berubah jadi " + statusBaru,
                "sisa_kuota", (data.rute != null ? data.rute.kuota_tersisa : 0)
        )).build();
    }

    @DELETE
    @Path("/hapus/{id}")
    @Transactional
    public Response hapusPendaftar(@PathParam("id") Long id) {
        PendaftaranMudik data = PendaftaranMudik.findById(id);
        if (data == null) return Response.status(404).entity(Map.of("error", "Data sudah tidak ada")).build();

        String status = (data.status_pendaftaran != null) ? data.status_pendaftaran.toUpperCase() : "UNKNOWN";
        String namaRute = (data.rute != null) ? data.rute.tujuan : "Unknown";

        if ("DITERIMA".equals(status)) {
            if (data.rute != null) {
                data.rute.kuota_tersisa += 1;
                data.rute.persist();
                System.out.println(">>> DELETE: Mengembalikan 1 Tiket ke " + namaRute);
            }
        } else {
            System.out.println(">>> DELETE: Hapus data (Status: " + status + "). Tidak nambah kuota.");
        }
        data.delete();
        return Response.ok(Map.of(
                "status", "SUKSES",
                "pesan", "Data berhasil dihapus selamanya."
        )).build();
    }
}