package com.mudik.resource;

import com.mudik.model.Kendaraan;
import com.mudik.model.PendaftaranMudik;
import com.mudik.model.Rute;
import com.mudik.service.ExcelService;
import com.mudik.service.WhatsAppService;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Path("/api/admin")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class AdminResource {

    @Inject
    ExcelService excelService;

    @Inject
    WhatsAppService waService;

    // =================================================================
    // 1. DASHBOARD & DATA PENDAFTAR
    // =================================================================
    @GET
    @Path("/pendaftar")
    public Response getAllPendaftar(@QueryParam("rute_id") Long ruteId) {
        List<PendaftaranMudik> list;
        if (ruteId != null) {
            list = PendaftaranMudik.list("rute.rute_id = ?1 ORDER BY created_at DESC", ruteId);
        } else {
            list = PendaftaranMudik.list("ORDER BY created_at DESC");
        }

        List<Map<String, Object>> result = list.stream().map(p -> {
            Map<String, Object> map = new HashMap<>();

            // Data Dasar
            map.put("id", p.pendaftaran_id);
            map.put("nama_peserta", p.nama_peserta);
            map.put("nik_peserta", p.nik_peserta);
            map.put("jenis_kelamin", p.jenis_kelamin);
            map.put("kategori", (p.kategori_penumpang != null) ? p.kategori_penumpang : "");
            map.put("status", (p.status_pendaftaran != null) ? p.status_pendaftaran : "UNKNOWN");
            map.put("kode_booking", (p.kode_booking != null) ? p.kode_booking : "-");

            // Grouping Keluarga
            map.put("id_keluarga", (p.user != null) ? p.user.user_id : 0);
            map.put("nama_kepala_keluarga", (p.user != null) ? p.user.nama_lengkap : "Tanpa Akun");

            // Data Rute & Bus
            map.put("rute_tujuan", (p.rute != null) ? p.rute.tujuan : "Unknown");
            map.put("tgl_berangkat", (p.rute != null) ? p.rute.getFormattedDate() : "-");
            map.put("nama_bus", (p.kendaraan != null) ? p.kendaraan.nama_armada : "Belum Plotting");
            map.put("plat_nomor", (p.kendaraan != null) ? p.kendaraan.plat_nomor : "-");
            map.put("titik_jemput", (p.alamat_rumah != null) ? p.alamat_rumah: "-");

            // Logic HP & Foto
            String targetHp = (p.no_hp_peserta != null && p.no_hp_peserta.length() > 5)
                    ? p.no_hp_peserta : ((p.user != null) ? p.user.no_hp : "");
            map.put("no_hp_target", targetHp);

            if (p.foto_identitas_path != null) {
                map.put("foto_bukti", "/uploads/" + new File(p.foto_identitas_path).getName());
            }

            // 🔥 LINK WA PAKAI SERVICE (LEBIH BERSIH) 🔥
            map.put("link_wa_terima", waService.generateLink(targetHp, "TERIMA", p));
            map.put("link_wa_tolak", waService.generateLink(targetHp, "TOLAK_DATA", p));
            map.put("link_wa_verif", waService.generateLink(targetHp, "VERIFIKASI", p));

            return map;
        }).collect(Collectors.toList());

        return Response.ok(result).build();
    }

    // =================================================================
    // 2. VERIFIKASI KELUARGA (TERIMA/TOLAK/RESET) - FIXED LOGIC
    // =================================================================
    @PUT
    @Path("/verifikasi-keluarga/{userId}")
    @Transactional
    public Response verifikasiKeluarga(@PathParam("userId") Long userId, Map<String, String> body) {
        String aksi = body.get("status"); // TERIMA, TOLAK, RESET
        if (aksi == null) return Response.status(400).entity(Map.of("error", "Status wajib diisi")).build();

        // Ambil SEMUA data keluarga
        List<PendaftaranMudik> keluarga = PendaftaranMudik.list("user.user_id", userId);
        if (keluarga.isEmpty()) return Response.status(404).entity(Map.of("error", "Data keluarga tidak ditemukan")).build();

        int count = 0;

        // Tentukan target status string biar gampang compare
        String targetStatus = "RESET".equals(aksi) ? "MENUNGGU_VERIFIKASI" : aksi;

        for (PendaftaranMudik p : keluarga) {
            String oldStatus = p.status_pendaftaran;

            // 1. Skip kalau status tujuannya SAMA PERSIS dengan sekarang
            if (targetStatus.equalsIgnoreCase(oldStatus)) continue;

            // -------------------------------------------------------------
            // ⚖️ LOGIKA KUOTA RUTE (Terpisah dari Bus)
            // -------------------------------------------------------------
            Rute rute = p.rute;
            if (rute != null) {
                // A. KASUS TOLAK: Balikin Kuota Rute
                // (Hanya kalau sebelumnya dia menempati kuota / bukan DITOLAK)
                if ("DITOLAK".equals(aksi)) {
                    if (!"DITOLAK".equals(oldStatus) && !"DIBATALKAN".equals(oldStatus)) {
                        if (rute.kuota_terisi > 0) {
                            rute.kuota_terisi -= 1; // 🟢 BALIKIN KUOTA
                        }
                    }
                }

                // B. KASUS PEMULIHAN (Dari Ditolak -> Diterima/Reset): Ambil Kuota Lagi
                // (Kalau sebelumnya DITOLAK/BATAL, berarti dia belum punya kursi, jadi harus ambil lagi)
                else if ("DITOLAK".equals(oldStatus) || "DIBATALKAN".equals(oldStatus)) {
                    // Cek sisa kuota dulu, masih ada gak?
                    if (rute.getSisaKuota() <= 0) {
                        // Kalau kuota habis, skip orang ini aja (jangan errorin satu keluarga)
                        // Atau bisa throw exception kalau mau strict
                        continue;
                    }
                    rute.kuota_terisi += 1; // 🔴 AMBIL KUOTA LAGI
                }
            }

            // -------------------------------------------------------------
            // 🚌 LOGIKA BUS (Hapus dari Bus kalau Ditolak/Reset)
            // -------------------------------------------------------------
            // Kalau status akhirnya bukan DITERIMA/TERKONFIRMASI, tendang dari bus
            if ("DITOLAK".equals(targetStatus) || "MENUNGGU_VERIFIKASI".equals(targetStatus)) {
                if (p.kendaraan != null) {
                    p.kendaraan.terisi -= 1; // Kosongkan kursi bus
                    p.kendaraan = null;      // Lepas relasi bus
                }
            }

            // Update Status Akhir
            p.status_pendaftaran = targetStatus;

            p.persist(); // Simpan Perubahan Pendaftaran
            if (rute != null) rute.persist(); // Simpan Perubahan Kuota Rute

            count++;
        }

        return Response.ok(Map.of(
                "status", "BERHASIL",
                "pesan", count + " anggota keluarga berhasil di-update ke status " + aksi
        )).build();
    }
    // =================================================================
    // 3. PLOTTING BUS (LOGIC: LOCK H-3 & SWAP BUS)
    // =================================================================
    @POST
    @Path("/assign-bus")
    @Transactional
    public Response assignBus(@QueryParam("user_id") Long userId, @QueryParam("kendaraan_id") Long kendaraanId) {
        // 1. Cek Bus Baru
        Kendaraan busBaru = Kendaraan.findById(kendaraanId);
        if (busBaru == null) return Response.status(404).entity(Map.of("error", "Bus tidak ditemukan")).build();

        // 2. Cek Status Wajib TERKONFIRMASI
        List<PendaftaranMudik> keluarga = PendaftaranMudik.list("user.user_id = ?1 AND status_pendaftaran = 'TERKONFIRMASI'", userId);

        if (keluarga.isEmpty()) {
            long belumKonfirmasi = PendaftaranMudik.count("user.user_id = ?1 AND status_pendaftaran = 'DITERIMA'", userId);
            if (belumKonfirmasi > 0) {
                return Response.status(400).entity(Map.of("error", "User belum Konfirmasi Kehadiran (Link WA).")).build();
            }
            return Response.status(400).entity(Map.of("error", "Data tidak ditemukan atau belum SIAP.")).build();
        }

        // 3. Cek Kapasitas
        if (busBaru.terisi + keluarga.size() > busBaru.kapasitas_total) {
            return Response.status(400).entity(Map.of("error", "Bus Penuh! Sisa: " + (busBaru.kapasitas_total - busBaru.terisi))).build();
        }

        // 4. Proses Plotting / Pindah Bus
        for (PendaftaranMudik p : keluarga) {
            // Pindah Bus (Swap Logic)
            if (p.kendaraan != null) {
                if (!p.kendaraan.id.equals(busBaru.id)) {
                    p.kendaraan.terisi -= 1;
                    p.kendaraan.persist();
                } else {
                    continue; // Bus sama, skip
                }
            } else {
                // Baru dapet bus pertama kali -> Update Kuota FIX
                if (p.rute.kuota_fix == null) p.rute.kuota_fix = 0;
                p.rute.kuota_fix += 1;
            }

            p.kendaraan = busBaru;
            busBaru.terisi += 1;
            p.persist();
        }
        busBaru.persist();

        return Response.ok(Map.of("status", "BERHASIL", "pesan", "Keluarga masuk bus " + busBaru.nama_armada)).build();
    }

    // =================================================================
    // 4. STATISTIK
    // =================================================================
    @GET
    @Path("/stats")
    public Response getDashboardStats() {
        long totalPendaftar = PendaftaranMudik.count();
        long totalDiterima = PendaftaranMudik.count("status_pendaftaran", "DITERIMA");
        long totalSiap = PendaftaranMudik.count("status_pendaftaran", "TERKONFIRMASI");

        long sisaKuota = Rute.listAll().stream().mapToLong(r -> ((Rute) r).getSisaKuota()).sum();

        List<Map<String, Object>> statsRute = Rute.listAll().stream().map(r -> {
            Rute rute = (Rute) r;
            long terisi = PendaftaranMudik.count("rute.rute_id = ?1 AND (status_pendaftaran = 'DITERIMA' OR status_pendaftaran = 'TERKONFIRMASI')", rute.rute_id);
            Map<String, Object> map = new HashMap<>();
            map.put("tujuan", rute.tujuan);
            map.put("sisa_kuota", rute.getSisaKuota());
            map.put("terisi", terisi);
            map.put("total_kursi", rute.kuota_total);
            return map;
        }).toList();

        return Response.ok(Map.of("total_masuk", totalPendaftar, "total_diterima", totalDiterima, "total_siap", totalSiap, "sisa_kuota_global", sisaKuota, "detail_rute", statsRute)).build();
    }

    // =================================================================
    // 5. EXPORT EXCEL
    // =================================================================
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
            return Response.ok(excelService.generateLaporanExcel(list))
                    .header("Content-Disposition", "attachment; filename=Rekap_Mudik.xlsx").build();
        } catch (IOException e) {
            return Response.serverError().entity("Gagal generate Excel").build();
        }
    }
}