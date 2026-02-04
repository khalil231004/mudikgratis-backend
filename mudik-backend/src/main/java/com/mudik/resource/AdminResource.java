package com.mudik.resource;

import com.mudik.model.PendaftaranMudik;
import com.mudik.model.Rute;
import com.mudik.service.ExcelService;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.io.File;
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

    // =================================================================
    // BAGIAN 1: GET DATA (DASHBOARD)
    // =================================================================
    @GET
    @Path("/pendaftar")
    public Response getAllPendaftar(@QueryParam("rute_id") Long ruteId) {
        List<PendaftaranMudik> list;

        // Filter Rute
        if (ruteId != null) {
            list = PendaftaranMudik.list("rute.rute_id = ?1 ORDER BY created_at DESC", ruteId);
        } else {
            list = PendaftaranMudik.list("ORDER BY created_at DESC");
        }

        // Mapping Data
        List<Map<String, Object>> result = list.stream().map(p -> {
            // Data Dasar
            String nama = p.nama_peserta;
            String kode = (p.kode_booking != null) ? p.kode_booking : "-";
            String rute = (p.rute != null) ? p.rute.tujuan : "Unknown";

            // Logic HP (Ambil dari User kalau peserta kosong)
            String targetHp = (p.no_hp_peserta != null && p.no_hp_peserta.length() > 5)
                    ? p.no_hp_peserta
                    : ((p.user != null) ? p.user.no_hp : "");

            // Logic Foto (SIMPEL & KUAT)
            String fotoUrl = "";
            if (p.foto_identitas_path != null && !p.foto_identitas_path.trim().isEmpty()) {
                String rawPath = p.foto_identitas_path;
                // Ambil nama file saja, buang path folder C:/ atau /home/
                String filename = new File(rawPath).getName();
                fotoUrl = "/uploads/" + filename;
            }

            // Siapkan Map JSON
            Map<String, Object> map = new HashMap<>();
            map.put("id", p.pendaftaran_id);
            map.put("nama_peserta", p.nama_peserta);
            map.put("nik_peserta", p.nik_peserta);
            map.put("jenis_kelamin", p.jenis_kelamin);
            map.put("kategori", (p.kategori_penumpang != null) ? p.kategori_penumpang : "");
            map.put("status", (p.status_pendaftaran != null) ? p.status_pendaftaran : "UNKNOWN");

            // Data Tambahan untuk Frontend Baru
            map.put("rute_tujuan", rute);
            map.put("tgl_iso", (p.rute != null && p.rute.tanggal_keberangkatan != null) ? p.rute.tanggal_keberangkatan.toString() : "");
            map.put("tgl_berangkat", (p.rute != null) ? p.rute.getFormattedDate() : "-");
            map.put("nama_bus", (p.rute != null && p.rute.nama_bus != null) ? p.rute.nama_bus : "Armada Dishub");
            map.put("titik_jemput", (p.alamat_rumah != null) ? p.alamat_rumah: "-");
            map.put("no_hp_target", targetHp);
            map.put("foto_bukti", fotoUrl);

            // === LINK WA (TEMPLATE PAK KIKI) ===
            // 1. Link Terima (Info Keberangkatan)
            map.put("link_wa_terima", generateWaLink(targetHp, "TERIMA", p));
            // 2. Link Tolak (Data Tidak Valid - Default)
            map.put("link_wa_tolak", generateWaLink(targetHp, "TOLAK_DATA", p));
            // 3. Link Verifikasi (Standar)
            map.put("link_wa_verif", generateWaLink(targetHp, "VERIFIKASI", p));

            return map;
        }).collect(Collectors.toList());

        return Response.ok(result).build();
    }

    // =================================================================
    // BAGIAN 2: VERIFIKASI (TERIMA / TOLAK)
    // =================================================================
    @PUT
    @Path("/verifikasi/{id}")
    @Transactional
    public Response verifikasiManual(@PathParam("id") Long id, Map<String, String> body) {
        PendaftaranMudik data = PendaftaranMudik.findById(id);
        if (data == null) return Response.status(404).entity(Map.of("error", "Data hilang!")).build();

        String pilihan = body.get("status");
        if (pilihan == null) return Response.status(400).entity(Map.of("error", "Status wajib diisi!")).build();

        String statusBaru = pilihan.toUpperCase();
        String statusLama = (data.status_pendaftaran != null) ? data.status_pendaftaran : "UNKNOWN";

        // Kalau status sama, skip
        if (statusLama.equals(statusBaru)) {
            return Response.ok(Map.of("status", "TETAP", "pesan", "Status tidak berubah.")).build();
        }

        // Logic Kuota
        boolean isBaruGagal = "DITOLAK".equals(statusBaru) || "DIBATALKAN".equals(statusBaru);
        boolean isLamaSukses = "DITERIMA".equals(statusLama);

        // 1. Balikin Kuota (Kalau dulu Diterima, sekarang Ditolak/Batal)
        if (isBaruGagal && isLamaSukses) {
            if (data.rute != null) {
                data.rute.kuota_tersisa += 1;
            }
        }
        // 2. Kurangi Kuota (Kalau dulu Belum, sekarang Diterima)
        else if ("DITERIMA".equals(statusBaru) && !isLamaSukses) {
            if (data.rute != null) {
                if (data.rute.kuota_tersisa <= 0) {
                    return Response.status(400).entity(Map.of("error", "Gagal! Kuota Bus Habis.")).build();
                }
                data.rute.kuota_tersisa -= 1;
            }
        }

        data.status_pendaftaran = statusBaru;
        data.persist();

        return Response.ok(Map.of("status", "SUKSES", "pesan", "Status berubah jadi " + statusBaru)).build();
    }

    // =================================================================
    // BAGIAN 3: BATALKAN (TOMBOL SAMPAH/BATAL)
    // =================================================================
    @PUT
    @Path("/batalkan/{id}")
    @Transactional
    public Response batalkanPendaftaran(@PathParam("id") Long id) {
        PendaftaranMudik p = PendaftaranMudik.findById(id);
        if (p == null) return Response.status(404).entity(Map.of("error", "Data tidak ditemukan")).build();

        // Validasi
        if ("BERANGKAT".equals(p.status_pendaftaran)) {
            return Response.status(400).entity(Map.of("error", "Sudah berangkat, tidak bisa batal.")).build();
        }

        // Kalau sudah batal, return sukses aja biar frontend gak error
        if ("DIBATALKAN".equals(p.status_pendaftaran)) {
            return Response.ok(Map.of("status", "SUKSES", "pesan", "Sudah dibatalkan sebelumnya.")).build();
        }

        // BALIKIN KUOTA jika sebelumnya DITERIMA
        if ("DITERIMA".equals(p.status_pendaftaran) && p.rute != null) {
            p.rute.kuota_tersisa += 1;
        }

        p.status_pendaftaran = "DIBATALKAN";
        p.persist();

        return Response.ok(Map.of(
                "status", "SUKSES",
                "pesan", "Pendaftaran dibatalkan. Kuota dikembalikan."
        )).build();
    }

    // =================================================================
    // BAGIAN 4: STATISTIK & EXPORT
    // =================================================================
    @GET
    @Path("/stats")
    public Response getDashboardStats() {
        long totalPendaftar = PendaftaranMudik.count();
        long totalDiterima = PendaftaranMudik.count("status_pendaftaran", "DITERIMA");
        long sisaKuota = Rute.listAll().stream()
                .mapToLong(r -> ((Rute) r).kuota_tersisa)
                .sum();

        List<Map<String, Object>> statsRute = Rute.listAll().stream().map(r -> {
            Rute rute = (Rute) r;
            long terisi = PendaftaranMudik.count("rute.rute_id = ?1 AND status_pendaftaran = 'DITERIMA'", rute.rute_id);
            long totalKursi = rute.kuota_tersisa + terisi;

            Map<String, Object> map = new HashMap<>();
            map.put("tujuan", rute.tujuan);
            map.put("sisa_kuota", rute.kuota_tersisa);
            map.put("terisi", terisi);
            map.put("total_kursi", totalKursi);
            return map;
        }).collect(Collectors.toList());

        return Response.ok(Map.of(
                "total_masuk", totalPendaftar,
                "total_diterima", totalDiterima,
                "sisa_kuota_global", sisaKuota,
                "detail_rute", statsRute
        )).build();
    }

    @GET
    @Path("/export")
    @Produces("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    public Response exportExcel(@QueryParam("rute_id") Long ruteId) {
        try {
            List<PendaftaranMudik> list;
            if (ruteId != null) {
                list = PendaftaranMudik.list("rute.rute_id = ?1 ORDER BY nama_peserta ASC", ruteId);
            } else {
                list = PendaftaranMudik.list("ORDER BY rute.tujuan ASC, nama_peserta ASC");
            }
            byte[] excelBytes = excelService.generateLaporanExcel(list);
            return Response.ok(excelBytes)
                    .header("Content-Disposition", "attachment; filename=Rekap_Mudik_2026.xlsx")
                    .build();
        } catch (IOException e) {
            return Response.serverError().entity("Gagal generate Excel").build();
        }
    }

    // =================================================================
    // BAGIAN 5: WA LINK GENERATOR (TEMPLATE PAK KIKI)
    // =================================================================
    private String generateWaLink(String noHp, String tipe, PendaftaranMudik p) {
        if (noHp == null || noHp.length() < 7) return "#";

        // 1. Format HP (081 -> 6281)
        String hpFormat = noHp.replaceAll("[^0-9]", "");
        if (hpFormat.startsWith("0")) hpFormat = "62" + hpFormat.substring(1);

        String nama = p.nama_peserta;
        String rute = (p.rute != null) ? p.rute.tujuan : "Aceh";
        // Ambil tanggal dari DB, kalau null pakai default template
        String tglBerangkat = (p.rute != null && p.rute.tanggal_keberangkatan != null)
                ? p.rute.getFormattedDate()
                : "27 Maret 2026";

        StringBuilder pesan = new StringBuilder();

        // 2. Pilih Template
        if (tipe.equals("TERIMA")) {
            // [Informasi Keberangkatan - Template Pak Kiki]
            pesan.append("Salam Bapak/Ibu *").append(nama).append("*\n");
            pesan.append("Kami informasikan bahwa keberangkatan anda dan rombongan pada :\n\n");
            pesan.append("Hari/tanggal : ").append(tglBerangkat).append("\n");
            pesan.append("Registrasi : 06.30 s.d 07.30 WIB\n");
            pesan.append("Keberangkatan : 08.30 WIB\n");
            pesan.append("Tempat : Depo Trans koetaradja, Komplek Terminal Tipe A Batoh Banda Aceh\n");
            pesan.append("Rute : Banda Aceh - ").append(rute).append("\n\n");
            pesan.append("Harap tiba tepat waktu.\n");
            pesan.append("Pesan ini merupakan tanda bahwa Anda sebagai Peserta Mudik Gratis Bersama Pemerintah Aceh 2026.\n");
            pesan.append("Harap tunjukkan pesan ini ke petugas.\n");
            pesan.append("Terima kasih.");

        } else if (tipe.equals("TOLAK_DATA")) {
            // [Status Mudik Ditolak - Data Tidak Valid]
            pesan.append("Mohon maaf, pendaftaran Anda sebagai peserta Mudik Gratis Bersama Pemerintah Aceh ditolak, DATA TIDAK VALID.\n");
            pesan.append("Terima kasih");

        } else if (tipe.equals("TOLAK_KUOTA")) {
            // [Status Mudik Ditolak - Kuota Penuh]
            pesan.append("Mohon maaf, pendaftaran Anda sebagai peserta Mudik Gratis Bersama Pemerintah Aceh ditolak, KUOTA SUDAH PENUH.\n");
            pesan.append("Terima kasih");

        } else if (tipe.equals("VERIFIKASI")) {
            // [Status Menunggu Verifikasi]
            pesan.append("Salam!\n");
            pesan.append("Terima kasih Bapak/Ibu *").append(nama).append("* telah mendaftar.\n");
            pesan.append("Saat ini data Anda sedang dalam proses *VERIFIKASI* oleh admin.\n");
            pesan.append("Mohon pastikan foto identitas (KTP/KK) yang diunggah dapat terbaca jelas.\n");
            pesan.append("Kami akan menghubungi Anda kembali setelah verifikasi selesai.");
        }

        try {
            return "https://wa.me/" + hpFormat + "?text=" + URLEncoder.encode(pesan.toString(), StandardCharsets.UTF_8);
        } catch (Exception e) { return "#"; }
    }
}