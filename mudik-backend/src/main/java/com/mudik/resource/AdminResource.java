package com.mudik.resource;

import com.mudik.model.PendaftaranMudik;
import jakarta.annotation.security.RolesAllowed;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import com.mudik.service.ExcelService;
import jakarta.inject.Inject;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Path("/api/admin")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RolesAllowed("ADMIN")
public class AdminResource {

    @Inject
    ExcelService excelService;

    public enum PilihanStatus { DITERIMA, DITOLAK, BATAL }

    // --- 1. LIHAT DATA (KODE LAMA - AMAN) ---
    @GET
    @Path("/pendaftar")
    public Response getAllPendaftar(@QueryParam("rute_id") Long ruteId) {

        List<PendaftaranMudik> list;
        // Logic Filter Rute + Sort Terbaru
        if (ruteId != null) {
            list = PendaftaranMudik.list("rute.rute_id = ?1 ORDER BY created_at DESC", ruteId);
        } else {
            list = PendaftaranMudik.list("ORDER BY created_at DESC");
        }

        List<Map<String, Object>> result = list.stream().map(p -> {
            String nama = p.nama_peserta;
            String kode = (p.kode_booking != null) ? p.kode_booking : "-";
            String rute = (p.rute != null) ? p.rute.tujuan : "Unknown";

            // --- 🧠 LOGIC CERDAS: PILIH NO HP ---
            String targetHp;

            // Cek 1: Apakah Peserta punya HP sendiri? (Dan valid > 5 digit)
            if (p.no_hp_peserta != null && p.no_hp_peserta.length() > 5) {
                targetHp = p.no_hp_peserta; // Kirim ke Peserta
            }
            // Cek 2: Kalau HP Peserta kosong (Anak/Bayi/Mager), pake HP Akun (Bapaknya)
            else {
                targetHp = (p.user != null) ? p.user.no_hp : ""; // Kirim ke Bapaknya
            }

            // Fix Path Gambar
            String fotoUrl = (p.foto_identitas_path != null && p.foto_identitas_path.contains("uploads"))
                    ? "/uploads/" + p.foto_identitas_path.substring(p.foto_identitas_path.lastIndexOf(java.io.File.separator)+1)
                    : null;

            Map<String, Object> map = new HashMap<>();
            map.put("id", p.pendaftaran_id);
            map.put("nama_peserta", p.nama_peserta);
            map.put("nik_peserta", p.nik_peserta);
            map.put("jenis_kelamin", p.jenis_kelamin);
            map.put("tanggal_lahir", (p.tanggal_lahir != null) ? p.tanggal_lahir.toString() : "");
            map.put("kategori", (p.kategori_penumpang != null) ? p.kategori_penumpang : "");
            map.put("status", (p.status_pendaftaran != null) ? p.status_pendaftaran : "UNKNOWN");
            map.put("rute_tujuan", rute);
            map.put("titik_jemput", (p.titik_jemput != null) ? p.titik_jemput : "-");

            // Kirim info HP yang DIPAKAI ke frontend biar Admin tau ini nomor siapa
            map.put("no_hp_target", targetHp);

            map.put("foto_bukti", (fotoUrl != null) ? fotoUrl : "");

            // --- GENERATE LINK PAKE targetHp YANG UDAH DIPILIH TADI ---
            map.put("link_wa_terima", generateWaLink(targetHp, "TERIMA", nama, kode, rute));
            map.put("link_wa_tolak", generateWaLink(targetHp, "TOLAK", nama, kode, rute));
            map.put("link_wa_info", generateWaLink(targetHp, "INFO", nama, kode, rute));

            return map;
        }).collect(Collectors.toList());

        return Response.ok(result).build();
    }
    // --- 2. FITUR BARU: EXPORT EXCEL (SAYA TAMBAHKAN DISINI) ---
    @GET
    @Path("/export")
    @Produces("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    public Response exportExcel(@QueryParam("rute_id") Long ruteId) {
        try {
            List<PendaftaranMudik> list;
            // Kalau ada filter rute, export sesuai rute aja
            if (ruteId != null) {
                list = PendaftaranMudik.list("rute.rute_id = ?1 ORDER BY nama_peserta ASC", ruteId);
            } else {
                list = PendaftaranMudik.list("ORDER BY rute.tujuan ASC, nama_peserta ASC");
            }

            // Panggil Service Excel yang udah lu inject di atas
            byte[] excelBytes = excelService.generateLaporanExcel(list);

            return Response.ok(excelBytes)
                    .header("Content-Disposition", "attachment; filename=Rekap_Mudik_2026.xlsx")
                    .build();

        } catch (IOException e) {
            e.printStackTrace();
            return Response.serverError().entity("Gagal generate Excel: " + e.getMessage()).build();
        }
    }

    // --- 3. VERIFIKASI (KODE LAMA - AMAN) ---
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

        if (statusLama.equals(statusBaru)) return Response.ok(Map.of("status", "TETAP", "pesan", "Status tidak berubah.")).build();

        boolean isBaruGagal = "DITOLAK".equals(statusBaru) || "BATAL".equals(statusBaru);
        boolean isLamaDiterima = "DITERIMA".equals(statusLama);

        if (isBaruGagal && isLamaDiterima) {
            if (data.rute != null) {
                data.rute.kuota_tersisa += 1;
                data.rute.persist();
            }
        } else if ("DITERIMA".equals(statusBaru)) {
            if (data.rute != null) {
                if (data.rute.kuota_tersisa <= 0) return Response.status(400).entity(Map.of("status", "GAGAL", "error", "Kuota Habis!")).build();
                data.rute.kuota_tersisa -= 1;
                data.rute.persist();
            }
        }

        data.status_pendaftaran = statusBaru;
        data.persist();
        return Response.ok(Map.of("status", "SUKSES", "pesan", "Status berubah jadi " + statusBaru)).build();
    }

    @DELETE
    @Path("/hapus/{id}")
    @Transactional
    public Response hapusPendaftar(@PathParam("id") Long id) {
        PendaftaranMudik data = PendaftaranMudik.findById(id);
        if (data == null) return Response.status(404).entity(Map.of("error", "Data tidak ada")).build();

        if ("DITERIMA".equals(data.status_pendaftaran)) {
            if (data.rute != null) {
                data.rute.kuota_tersisa += 1;
                data.rute.persist();
            }
        }
        data.delete();
        return Response.ok(Map.of("status", "SUKSES", "pesan", "Data dihapus.")).build();
    }
    // COPY CODE INI KE DALAM AdminResource.java

    @GET
    @Path("/cek/{nik}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response cekStatus(@PathParam("nik") String nik) {
        // Cari data di database berdasarkan kolom "nik_peserta"
        // Pastikan PendaftaranMudik sudah di-import
        PendaftaranMudik pendaftar = PendaftaranMudik.find("nik_peserta", nik).firstResult();

        if (pendaftar == null) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(Map.of("message", "NIK tidak ditemukan"))
                    .build();
        }

        return Response.ok(pendaftar).build();
    }

    @GET
    @Path("/stats")
    public Response getDashboardStats() {
        // 1. Total Pendaftar
        long totalPendaftar = PendaftaranMudik.count();

        // 2. Total Diterima
        long totalDiterima = PendaftaranMudik.count("status_pendaftaran", "DITERIMA");

        // 3. Sisa Kuota Global
        long sisaKuota = com.mudik.model.Rute.listAll().stream()
                .mapToLong(r -> ((com.mudik.model.Rute) r).kuota_tersisa)
                .sum();

        // 4. Statistik Per Rute
        List<Map<String, Object>> statsRute = com.mudik.model.Rute.listAll().stream().map(r -> {
            com.mudik.model.Rute rute = (com.mudik.model.Rute) r;

            // Hitung manual
            long terisi = PendaftaranMudik.count("rute.rute_id = ?1 AND status_pendaftaran = 'DITERIMA'", rute.rute_id);
            long totalKursiHitungan = rute.kuota_tersisa + terisi;

            // --- PERBAIKAN DISINI (PAKE HASHMAP BIAR GAK ERROR TIPE DATA) ---
            Map<String, Object> map = new HashMap<>();
            map.put("tujuan", rute.tujuan); // String
            map.put("sisa_kuota", rute.kuota_tersisa); // Long
            map.put("terisi", terisi); // Long
            map.put("total_kursi", totalKursiHitungan); // Long

            return map;
        }).collect(Collectors.toList());

        return Response.ok(Map.of(
                "total_masuk", totalPendaftar,
                "total_diterima", totalDiterima,
                "sisa_kuota_global", sisaKuota,
                "detail_rute", statsRute
        )).build();
    }

    // --- 5. WA GENERATOR (KODE LAMA - AMAN) ---
    private String generateWaLink(String noHp, String tipe, String nama, String kode, String tujuan) {
        if (noHp == null || noHp.length() < 7) return "#";
        String hpFormat = noHp.replaceAll("[^0-9]", "");
        if (hpFormat.startsWith("0")) hpFormat = "62" + hpFormat.substring(1);

        String pesan = "";
        if (tipe.equals("TERIMA")) {
            pesan = "Halo Kak *" + nama + "*! 👋\n\n" +
                    "Selamat! Pendaftaran Mudik Gratis tujuan *" + tujuan + "* telah *DITERIMA*.\n" +
                    "Kode Booking: *" + kode + "*\n\n" +
                    "Silakan unduh tiket di aplikasi. Mohon datang tepat waktu. Terima kasih!";

        } else if (tipe.equals("TOLAK")) {
            pesan = "Halo Kak *" + nama + "*. Mohon maaf, pendaftaran Mudik tujuan *" + tujuan + "* statusnya *DITOLAK*.\n\n" +
                    "Kemungkinan karena berkas tidak valid. Silakan cek aplikasi untuk detailnya.";

        } else {
            pesan = "Halo Kak *" + nama + "*! 👋\n\n" +
                    "Kami ingin menginfokan bahwa data pendaftaran Mudik Gratis Anda saat ini *SEDANG DALAM PROSES VERIFIKASI* oleh tim kami.\n\n" +
                    "Mohon kesediaannya menunggu update selanjutnya. Terima kasih! 🙏";
        }

        try { return "https://wa.me/" + hpFormat + "?text=" + URLEncoder.encode(pesan, StandardCharsets.UTF_8); }
        catch (Exception e) { return "#"; }
    }
}