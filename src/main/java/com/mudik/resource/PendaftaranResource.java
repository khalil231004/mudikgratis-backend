package com.mudik.resource;

import com.mudik.model.PendaftaranMudik;
import com.mudik.model.Rute;
import com.mudik.model.User;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.jboss.resteasy.reactive.RestForm;
import org.jboss.resteasy.reactive.multipart.FileUpload;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.Period;
import java.util.*;

@Path("/api/pendaftaran")
public class PendaftaranResource {

    // 🔥 PAKE ABSOLUTE PATH BIAR GAK ERROR 500 PAS UPLOAD
    private static final String UPLOAD_DIR = "/opt/mudik-v2/uploads/";

    // Form Data Wrapper (Sama kayak lama)
    public static class PendaftaranMultipartForm {
        @RestForm("nama_peserta") public List<String> nama_peserta;
        @RestForm("nik_peserta") public List<String> nik_peserta;
        @RestForm("jenis_kelamin") public List<String> jenis_kelamin;
        @RestForm("tanggal_lahir") public List<String> tanggal_lahir;
        @RestForm("jenis_identitas") public List<String> jenis_identitas;
        @RestForm("jenis_barang") public List<String> jenis_barang;
        @RestForm("ukuran_barang") public List<String> ukuran_barang;
        @RestForm("alamat_rumah") public List<String> alamat_rumah;
        @RestForm("no_hp_peserta") public List<String> no_hp_peserta;
        @RestForm("fotoBukti") public List<FileUpload> fotoBukti;
    }

    // ==========================================================
    // 1. GET RIWAYAT (MAPPING MANUAL - BIAR FRONTEND SENENG)
    // ==========================================================
    @GET
    @Path("/riwayat")
    @Produces(MediaType.APPLICATION_JSON)
    public Response riwayatPendaftaran(@HeaderParam("userId") Long userId) {
        // Validasi header userId
        if (userId == null) {
            return Response.status(401).entity(Map.of("error", "Unauthorized: User ID wajib diisi di header")).build();
        }

        // Ambil data user, urutkan dari terbaru
        List<PendaftaranMudik> list = PendaftaranMudik.list("user.user_id = ?1 ORDER BY created_at DESC", userId);

        // Mapping Manual ke JSON (Biar Rute & Bus kebaca Frontend)
        List<Map<String, Object>> result = new ArrayList<>();

        for (PendaftaranMudik p : list) {
            Map<String, Object> map = new HashMap<>();
            map.put("pendaftaran_id", p.pendaftaran_id);
            map.put("kode_booking", p.kode_booking != null ? p.kode_booking : "-");
            map.put("status_pendaftaran", p.status_pendaftaran);
            map.put("nama_peserta", p.nama_peserta);
            map.put("nik_peserta", p.nik_peserta);

            // Info Rute
            if (p.rute != null) {
                map.put("tujuan", p.rute.tujuan);
                map.put("tanggal_keberangkatan", p.rute.getFormattedDate());
            } else {
                map.put("tujuan", "-");
                map.put("tanggal_keberangkatan", "-");
            }

            // Info Bus (Ambil dari Kendaraan)
            if (p.kendaraan != null) {
                map.put("nama_bus", p.kendaraan.nama_armada);
                map.put("plat_nomor", p.kendaraan.plat_nomor);
            } else {
                map.put("nama_bus", "Menunggu Plotting");
                map.put("plat_nomor", "-");
            }

            result.add(map);
        }

        return Response.ok(result).build();
    }

    // ==========================================================
    // 2. POST DAFTAR (ANTI-CRASH & AUTO POTONG KUOTA)
    // ==========================================================
    @POST
    @Transactional
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Produces(MediaType.APPLICATION_JSON)
    public Response daftarBatch(
            @HeaderParam("userId") Long userId,
            @QueryParam("rute_id") Long ruteId,
            PendaftaranMultipartForm form
    ) {
        try {
            // Validasi Input Dasar
            if (userId == null) return Response.status(400).entity(Map.of("error", "Login dulu!")).build();
            if (ruteId == null) return Response.status(400).entity(Map.of("error", "Pilih rute dulu!")).build();

            // Cek Data Peserta Kosong
            if (form.nama_peserta == null || form.nama_peserta.isEmpty()) {
                return Response.status(400).entity(Map.of("error", "Data peserta kosong!")).build();
            }

            // Cek User & Rute
            User user = User.findById(userId);
            if (user == null) return Response.status(404).entity(Map.of("error", "User tidak ditemukan")).build();

            Rute rute = Rute.findById(ruteId);
            if (rute == null) return Response.status(404).entity(Map.of("error", "Rute tidak ditemukan")).build();

            int jumlahPesertaBaru = form.nama_peserta.size();

            // 🔥 VALIDASI SAFETY: Cek Jumlah NIK vs Nama (Biar gak IndexOutOfBounds)
            if (form.nik_peserta == null || form.nik_peserta.size() != jumlahPesertaBaru) {
                return Response.status(400).entity(Map.of("error", "Jumlah NIK tidak sesuai dengan jumlah Nama Peserta")).build();
            }

            // 🔥 VALIDASI 1: CEK KUOTA RUTE (CRITICAL)
            if (rute.getSisaKuota() < jumlahPesertaBaru) {
                return Response.status(400).entity(Map.of(
                        "error", "Mohon maaf, Kuota Rute Habis! Sisa tiket: " + rute.getSisaKuota()
                )).build();
            }

            // 🔥 VALIDASI 2: LIMIT KUOTA AKUN (Max 6)
            long sudahDaftar = PendaftaranMudik.count("user.user_id", userId);
            if (sudahDaftar + jumlahPesertaBaru > 6) {
                return Response.status(400).entity(Map.of(
                        "error", "Kuota akun penuh! Anda sisa slot: " + (6 - sudahDaftar)
                )).build();
            }

            // 🔥 VALIDASI 3: CEK NIK DUPLIKAT (Clean NIK)
            List<String> nikDuplikat = new ArrayList<>();
            for (String nik : form.nik_peserta) {
                String cleanNik = nik.trim();
                // Cek NIK, abaikan yg statusnya DIBATALKAN/DITOLAK
                long cekNik = PendaftaranMudik.count("nik_peserta = ?1 AND status_pendaftaran != 'DIBATALKAN'", cleanNik);
                if (cekNik > 0) {
                    nikDuplikat.add(cleanNik);
                }
            }
            if (!nikDuplikat.isEmpty()) {
                return Response.status(409).entity(Map.of(
                        "error", "NIK berikut sudah terdaftar: " + String.join(", ", nikDuplikat)
                )).build();
            }

            // PROSES SIMPAN DATA
            for (int i = 0; i < jumlahPesertaBaru; i++) {
                PendaftaranMudik p = new PendaftaranMudik();
                p.user = user;
                p.rute = rute;
                p.nama_peserta = form.nama_peserta.get(i).toUpperCase();

                // Safety Potong NIK (Biar gak error DB column length)
                String rawNik = form.nik_peserta.get(i).trim();
                if (rawNik.length() > 16) rawNik = rawNik.substring(0, 16);
                p.nik_peserta = rawNik;

                p.jenis_kelamin = (form.jenis_kelamin != null && i < form.jenis_kelamin.size()) ? form.jenis_kelamin.get(i) : "-";

                // Parse Tanggal (Safe Mode)
                try {
                    if(form.tanggal_lahir != null && i < form.tanggal_lahir.size()) {
                        LocalDate tgl = LocalDate.parse(form.tanggal_lahir.get(i));
                        p.tanggal_lahir = tgl;
                        int umur = Period.between(tgl, LocalDate.now()).getYears();
                        p.kategori_penumpang = (umur < 5) ? "ANAK" : "DEWASA";
                    } else {
                        p.tanggal_lahir = LocalDate.now();
                        p.kategori_penumpang = "DEWASA";
                    }
                } catch (Exception e) {
                    p.tanggal_lahir = LocalDate.of(2000, 1, 1);
                    p.kategori_penumpang = "DEWASA";
                }

                // Data Optional
                if (form.jenis_identitas != null && i < form.jenis_identitas.size()) p.jenis_identitas = form.jenis_identitas.get(i);
                if (form.jenis_barang != null && i < form.jenis_barang.size()) p.jenis_barang = form.jenis_barang.get(i);
                if (form.ukuran_barang != null && i < form.ukuran_barang.size()) p.ukuran_barang = form.ukuran_barang.get(i);
                if (form.alamat_rumah != null && i < form.alamat_rumah.size()) p.alamat_rumah = form.alamat_rumah.get(i);

                // HP
                if (form.no_hp_peserta != null && i < form.no_hp_peserta.size()) p.no_hp_peserta = form.no_hp_peserta.get(i);
                else p.no_hp_peserta = user.no_hp;

                // Upload Foto (Safe Mode - Jangan biarkan error foto bikin gagal daftar)
                try {
                    if (form.fotoBukti != null && i < form.fotoBukti.size() && form.fotoBukti.get(i) != null && form.fotoBukti.get(i).fileName() != null) {
                        p.foto_identitas_path = simpanFile(form.fotoBukti.get(i), p.nik_peserta);
                    }
                } catch (Exception e) {
                    System.out.println("❌ Gagal upload foto peserta " + i + ": " + e.getMessage());
                }

                p.status_pendaftaran = "MENUNGGU_VERIFIKASI";
                p.kode_booking = "MDK-" + System.currentTimeMillis() + "-" + (i + 1);

                p.persist();
            }

            // 🔥 UPDATE KUOTA RUTE LANGSUNG (Biar Realtime)
            if (rute.kuota_terisi == null) rute.kuota_terisi = 0;
            rute.kuota_terisi += jumlahPesertaBaru;
            rute.persist();

            return Response.ok(Map.of(
                    "status", "BERHASIL",
                    "pesan", jumlahPesertaBaru + " peserta berhasil didaftarkan!"
            )).build();

        } catch (Exception e) {
            e.printStackTrace();
            return Response.serverError().entity(Map.of("error", "Server Error: " + e.getMessage())).build();
        }
    }

    // =================================================================
    // 3. KONFIRMASI KEHADIRAN
    // =================================================================
    @PUT
    @Path("/konfirmasi-kehadiran/{userId}")
    @Transactional
    public Response konfirmasiKehadiran(@PathParam("userId") Long userId, Map<String, List<Long>> body) {
        List<Long> idsTetapIkut = body.get("ids_konfirmasi");
        if (idsTetapIkut == null) return Response.status(400).build();

        List<PendaftaranMudik> keluarga = PendaftaranMudik.list("user.user_id = ?1 AND status_pendaftaran = 'DITERIMA'", userId);

        if (keluarga.isEmpty()) {
            long sudahKonfirmasi = PendaftaranMudik.count("user.user_id = ?1 AND status_pendaftaran = 'TERKONFIRMASI'", userId);
            if (sudahKonfirmasi > 0) {
                return Response.ok(Map.of("status", "SUDAH_KONFIRMASI", "message", "Sudah konfirmasi.")).build();
            }
            return Response.status(404).entity(Map.of("error", "Tidak ada data.")).build();
        }

        int countHadir = 0;
        int countBatal = 0;

        for (PendaftaranMudik p : keluarga) {
            if (idsTetapIkut.contains(p.pendaftaran_id)) {
                p.status_pendaftaran = "TERKONFIRMASI";
                countHadir++;
            } else {
                p.status_pendaftaran = "DIBATALKAN";
                // Balikin kuota kalau batal
                if (p.rute != null && p.rute.kuota_terisi > 0) {
                    p.rute.kuota_terisi -= 1;
                }
                countBatal++;
            }
            p.persist();
        }

        return Response.ok(Map.of(
                "status", "BERHASIL",
                "message", countHadir + " Konfirmasi, " + countBatal + " Batal."
        )).build();
    }

    // =================================================================
    // 4. HELPER UPLOAD (ABSOLUTE PATH)
    // =================================================================
    private String simpanFile(FileUpload fileUpload, String nik) throws IOException {
        File folder = new File(UPLOAD_DIR);
        if (!folder.exists()) folder.mkdirs(); // Buat folder kalau belum ada

        String originalName = fileUpload.fileName();
        String ext = (originalName.contains(".")) ? originalName.substring(originalName.lastIndexOf(".")) : ".jpg";

        // Pake UUID biar gak bentrok
        String newName = "ktp-" + nik + "-" + UUID.randomUUID().toString().substring(0, 5) + ext;

        File dest = new File(folder, newName);
        Files.move(fileUpload.uploadedFile(), dest.toPath(), StandardCopyOption.REPLACE_EXISTING);

        // Return string path buat di DB
        return "uploads/" + newName;
    }
}