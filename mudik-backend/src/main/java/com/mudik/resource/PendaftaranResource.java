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

    private static final String UPLOAD_DIR = "uploads/";

    // Form Data Wrapper
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
        @RestForm("foto_bukti") public List<FileUpload> fotoBukti;
    }

    // --- 1. GET RIWAYAT (SINKRON DENGAN ADMIN) ---
    @GET
    @Path("/riwayat")
    @Produces(MediaType.APPLICATION_JSON)
    public Response riwayatPendaftaran(@HeaderParam("userId") Long userId) {
        if (userId == null) return Response.status(400).entity(Map.of("error", "User ID wajib diisi")).build();

        // Ambil data user
        List<PendaftaranMudik> list = PendaftaranMudik.list("user.user_id = ?1 ORDER BY created_at DESC", userId);

        // MAPPING MANUAL BIAR SINKRON SAMA ADMIN
        // Kita tidak return entity mentah, tapi JSON yang sudah dirapikan
        List<Map<String, Object>> result = new ArrayList<>();

        for (PendaftaranMudik p : list) {
            Map<String, Object> map = new HashMap<>();
            map.put("pendaftaran_id", p.pendaftaran_id);
            map.put("kode_booking", p.kode_booking != null ? p.kode_booking : "-");
            map.put("status_pendaftaran", p.status_pendaftaran);
            map.put("nama_peserta", p.nama_peserta);
            map.put("nik_peserta", p.nik_peserta);

            // Info Rute
            map.put("tujuan", p.rute != null ? p.rute.tujuan : "-");
            map.put("tanggal_keberangkatan", p.rute != null ? p.rute.getFormattedDate() : "-");

            // Info Bus (Ambil dari Kendaraan, BUKAN Rute)
            // Ini kunci biar data bus muncul di dashboard user
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

    // --- 2. POST DAFTAR (VALIDASI DUPLIKAT & KUOTA) ---
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
            // Validasi Dasar
            if (userId == null) return Response.status(400).entity(Map.of("error", "Login dulu!")).build();
            if (ruteId == null) return Response.status(400).entity(Map.of("error", "Pilih rute dulu!")).build();

            User user = User.findById(userId);
            if (user == null) return Response.status(404).entity(Map.of("error", "User tidak ditemukan")).build();

            Rute rute = Rute.findById(ruteId);
            if (rute == null) return Response.status(404).entity(Map.of("error", "Rute tidak ditemukan")).build();

            int jumlahPesertaBaru = form.nama_peserta.size();

            // 🔥 VALIDASI 1: LIMIT KUOTA AKUN (Max 6 orang per Akun)
            long sudahDaftar = PendaftaranMudik.count("user.user_id", userId);
            if (sudahDaftar + jumlahPesertaBaru > 6) {
                return Response.status(400).entity(Map.of(
                        "error", "Kuota akun penuh! Anda sudah mendaftarkan " + sudahDaftar + " orang. Sisa slot: " + (6 - sudahDaftar)
                )).build();
            }

            // 🔥 VALIDASI 2: CEK NIK DUPLIKAT (GLOBAL)
            // Loop cek setiap NIK yang mau didaftarkan
            for (String nik : form.nik_peserta) {
                long cekNik = PendaftaranMudik.count("nik_peserta", nik);
                if (cekNik > 0) {
                    return Response.status(400).entity(Map.of(
                            "error", "NIK " + nik + " sudah terdaftar di sistem! Tidak boleh daftar ganda."
                    )).build();
                }
            }

            // PROSES SIMPAN DATA
            for (int i = 0; i < jumlahPesertaBaru; i++) {
                PendaftaranMudik p = new PendaftaranMudik();
                p.user = user;
                p.rute = rute;
                p.nama_peserta = form.nama_peserta.get(i).toUpperCase();
                p.nik_peserta = form.nik_peserta.get(i);
                p.jenis_kelamin = form.jenis_kelamin.get(i);

                // Parse Tanggal & Kategori
                LocalDate tgl = LocalDate.parse(form.tanggal_lahir.get(i));
                p.tanggal_lahir = tgl;
                int umur = Period.between(tgl, LocalDate.now()).getYears();
                p.kategori_penumpang = (umur < 5) ? "ANAK" : "DEWASA";

                // Data Lain
                if (form.jenis_identitas != null) p.jenis_identitas = form.jenis_identitas.get(i);
                if (form.jenis_barang != null) p.jenis_barang = form.jenis_barang.get(i);
                if (form.ukuran_barang != null) p.ukuran_barang = form.ukuran_barang.get(i);
                if (form.alamat_rumah != null) p.alamat_rumah = form.alamat_rumah.get(i);

                // HP
                if (form.no_hp_peserta != null && i < form.no_hp_peserta.size()) {
                    p.no_hp_peserta = form.no_hp_peserta.get(i);
                } else {
                    p.no_hp_peserta = user.no_hp; // Default HP Akun
                }

                // Upload Foto
                if (form.fotoBukti != null && i < form.fotoBukti.size()) {
                    p.foto_identitas_path = simpanFile(form.fotoBukti.get(i), p.nik_peserta);
                }

                p.status_pendaftaran = "MENUNGGU_VERIFIKASI";
                p.kode_booking = "MDK-" + System.currentTimeMillis() + "-" + i;

                p.persist();
            }

            // Update Kuota Terisi Rute (Triple Kuota Logic - Opsional di sini, tapi bagus buat update real time)
            if (rute.kuota_terisi == null) rute.kuota_terisi = 0;
            // Kita tidak nambah kuota terisi disini dulu biar Admin yang validasi,
            // TAPI kalau mau langsung potong kuota, uncomment baris ini:
            // rute.kuota_terisi += jumlahPesertaBaru;

            return Response.ok(Map.of(
                    "status", "BERHASIL",
                    "pesan", jumlahPesertaBaru + " peserta berhasil didaftarkan!"
            )).build();

        } catch (Exception e) {
            e.printStackTrace();
            return Response.serverError().entity(Map.of("error", "Server Error: " + e.getMessage())).build();
        }
    }

    // Helper Upload
    private String simpanFile(FileUpload fileUpload, String nik) throws IOException {
        File folder = new File(UPLOAD_DIR);
        if (!folder.exists()) folder.mkdirs();
        String originalName = fileUpload.fileName();
        String ext = (originalName.contains(".")) ? originalName.substring(originalName.lastIndexOf(".")) : ".jpg";
        String newName = "ktp-" + nik + "-" + UUID.randomUUID().toString().substring(0, 5) + ext;
        File dest = new File(folder, newName);
        Files.move(fileUpload.uploadedFile(), dest.toPath(), StandardCopyOption.REPLACE_EXISTING);
        return UPLOAD_DIR + newName;
    }

    // =================================================================
    // 3. KONFIRMASI KEHADIRAN (USER KLIK LINK WA)
    // =================================================================
    @PUT
    @Path("/konfirmasi-kehadiran/{userId}")
    @Transactional
    public Response konfirmasiKehadiran(@PathParam("userId") Long userId, Map<String, List<Long>> body) {
        List<Long> idsTetapIkut = body.get("ids_konfirmasi"); // ID yang dicentang user
        if (idsTetapIkut == null) return Response.status(400).build();

        // Ambil yang statusnya DITERIMA (Belum konfirmasi)
        List<PendaftaranMudik> keluarga = PendaftaranMudik.list("user.user_id = ?1 AND status_pendaftaran = 'DITERIMA'", userId);

        if (keluarga.isEmpty()) {
            // Cek kalau mungkin mereka sudah konfirmasi sebelumnya
            long sudahKonfirmasi = PendaftaranMudik.count("user.user_id = ?1 AND status_pendaftaran = 'TERKONFIRMASI'", userId);
            if (sudahKonfirmasi > 0) {
                return Response.ok(Map.of("status", "SUDAH_KONFIRMASI", "message", "Anda sudah melakukan konfirmasi sebelumnya.")).build();
            }
            return Response.status(404).entity(Map.of("error", "Tidak ada data yang perlu dikonfirmasi.")).build();
        }

        int countHadir = 0;
        int countBatal = 0;

        for (PendaftaranMudik p : keluarga) {
            if (idsTetapIkut.contains(p.pendaftaran_id)) {
                // 🔥 UBAH STATUS JADI 'TERKONFIRMASI' (SYARAT PLOTTING)
                p.status_pendaftaran = "TERKONFIRMASI";
                countHadir++;
            } else {
                // Gak dicentang -> BATAL
                p.status_pendaftaran = "DIBATALKAN";
                // Balikin kuota rute (karena belum dapet bus, cuma balikin kuota_terisi/pending)
                if (p.rute != null && p.rute.kuota_terisi > 0) {
                    p.rute.kuota_terisi -= 1;
                }
                countBatal++;
            }
            p.persist();
        }

        return Response.ok(Map.of(
                "status", "BERHASIL",
                "message", countHadir + " orang Terkonfirmasi, " + countBatal + " orang Dibatalkan."
        )).build();
    }
}