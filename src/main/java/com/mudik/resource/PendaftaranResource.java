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

    // 🔥 SESUAIKAN DENGAN VPS (ABSOLUTE PATH)
    private static final String UPLOAD_DIR = "/opt/mudik-v2/uploads/";

    // 1. FORM DATA WRAPPER
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

    // Form Khusus Edit Data
    public static class EditForm {
        @RestForm("nama_peserta") public String nama_peserta;
        @RestForm("nik_peserta") public String nik_peserta;
        @RestForm("jenis_kelamin") public String jenis_kelamin;
        @RestForm("tanggal_lahir") public String tanggal_lahir;
        @RestForm("fotoBukti") public FileUpload fotoBukti;
    }

    // ==========================================================
    // 1. GET RIWAYAT (MANUAL MAPPING - PENTING BUAT FRONTEND)
    // ==========================================================
    @GET
    @Path("/riwayat")
    @Produces(MediaType.APPLICATION_JSON)
    public Response riwayat(@HeaderParam("userId") Long userId) {
        if (userId == null) return Response.status(401).entity(Map.of("error", "Unauthorized")).build();

        // Ambil data sort by terbaru
        List<PendaftaranMudik> list = PendaftaranMudik.list("user.user_id = ?1 ORDER BY created_at DESC", userId);

        // Mapping ke JSON bersih biar Frontend enak bacanya
        List<Map<String, Object>> result = new ArrayList<>();

        for (PendaftaranMudik p : list) {
            Map<String, Object> map = new HashMap<>();
            map.put("pendaftaran_id", p.pendaftaran_id);
            map.put("kode_booking", p.kode_booking);
            map.put("status_pendaftaran", p.status_pendaftaran);
            map.put("nama_peserta", p.nama_peserta);
            map.put("nik_peserta", p.nik_peserta);
            map.put("uuid", p.uuid); // Penting buat link QR

            // Info Rute
            if (p.rute != null) {
                map.put("tujuan", p.rute.tujuan);
                map.put("tanggal_keberangkatan", p.rute.getFormattedDate());
            } else {
                map.put("tujuan", "-");
                map.put("tanggal_keberangkatan", "-");
            }

            // Info Bus (KUNCI: Ambil dari Kendaraan, bukan Rute)
            if (p.kendaraan != null) {
                map.put("nama_bus", p.kendaraan.nama_armada);
                map.put("plat_nomor", p.kendaraan.plat_nomor);
            } else {
                map.put("nama_bus", "Belum Plotting"); // Default value
                map.put("plat_nomor", "-");
            }

            result.add(map);
        }

        return Response.ok(result).build();
    }

    // ==========================================================
    // 2. POST DAFTAR BATCH (LOGIC KUOTA & NIK FIXED)
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
            // Validasi Dasar
            if (userId == null) return Response.status(400).entity(Map.of("error", "Login dulu!")).build();
            if (ruteId == null) return Response.status(400).entity(Map.of("error", "Pilih rute dulu!")).build();

            User user = User.findById(userId);
            if (user == null) return Response.status(404).entity(Map.of("error", "User tidak ditemukan")).build();

            Rute rute = Rute.findById(ruteId);
            if (rute == null) return Response.status(404).entity(Map.of("error", "Rute tidak ditemukan")).build();

            int jumlahPesertaBaru = (form.nama_peserta != null) ? form.nama_peserta.size() : 0;
            if (jumlahPesertaBaru == 0) return Response.status(400).entity(Map.of("error", "Data kosong")).build();

            // 🔥 VALIDASI KUOTA RUTE (CRITICAL)
            if (rute.getSisaKuota() < jumlahPesertaBaru) {
                return Response.status(400).entity(Map.of(
                        "error", "Mohon maaf, Kuota Rute Habis! Sisa tiket: " + rute.getSisaKuota()
                )).build();
            }

            // Validasi Limit Akun
            long sudahDaftar = PendaftaranMudik.count("user.user_id", userId);
            if (sudahDaftar + jumlahPesertaBaru > 6) {
                return Response.status(400).entity(Map.of(
                        "error", "Kuota akun penuh! Sisa slot: " + (6 - sudahDaftar)
                )).build();
            }

            // 🔥 VALIDASI NIK DUPLIKAT
            List<String> nikDuplikat = new ArrayList<>();
            for (String nik : form.nik_peserta) {
                long cekNik = PendaftaranMudik.count("nik_peserta = ?1 AND status_pendaftaran != 'DIBATALKAN'", nik);
                if (cekNik > 0) nikDuplikat.add(nik);
            }
            if (!nikDuplikat.isEmpty()) {
                return Response.status(409).entity(Map.of("error", "NIK berikut sudah terdaftar: " + String.join(", ", nikDuplikat))).build();
            }

            // SIMPAN DATA
            for (int i = 0; i < jumlahPesertaBaru; i++) {
                PendaftaranMudik p = new PendaftaranMudik();
                p.user = user;
                p.rute = rute;
                p.nama_peserta = form.nama_peserta.get(i).toUpperCase();
                p.nik_peserta = form.nik_peserta.get(i);
                p.jenis_kelamin = form.jenis_kelamin.get(i);

                try {
                    LocalDate tgl = LocalDate.parse(form.tanggal_lahir.get(i));
                    p.tanggal_lahir = tgl;
                    int umur = Period.between(tgl, LocalDate.now()).getYears();
                    p.kategori_penumpang = (umur < 5) ? "ANAK" : "DEWASA";
                } catch (Exception e) {
                    p.tanggal_lahir = LocalDate.of(2000, 1, 1);
                    p.kategori_penumpang = "DEWASA";
                }

                if (form.jenis_identitas != null) p.jenis_identitas = form.jenis_identitas.get(i);
                if (form.no_hp_peserta != null && i < form.no_hp_peserta.size())
                    p.no_hp_peserta = form.no_hp_peserta.get(i);
                else p.no_hp_peserta = user.no_hp;

                // UPLOAD FOTO
                if (form.fotoBukti != null && i < form.fotoBukti.size() && form.fotoBukti.get(i) != null && form.fotoBukti.get(i).fileName() != null) {
                    p.foto_identitas_path = simpanFile(form.fotoBukti.get(i), p.nik_peserta);
                }

                p.status_pendaftaran = "MENUNGGU_VERIFIKASI";
                p.kode_booking = "MDK-" + System.currentTimeMillis() + "-" + (i + 1);

                p.persist();
            }

            // 🔥 UPDATE KUOTA RUTE (LANGSUNG POTONG)
            if (rute.kuota_terisi == null) rute.kuota_terisi = 0;
            rute.kuota_terisi += jumlahPesertaBaru;
            rute.persist();

            return Response.ok(Map.of("status", "BERHASIL", "pesan", "Daftar Sukses!")).build();
        } catch (Exception e) {
            e.printStackTrace();
            return Response.serverError().entity(Map.of("error", e.getMessage())).build();
        }
    }

    // ==========================================================
    // 3. FITUR PERBAIKI DATA (EDIT STATUS DITOLAK)
    // ==========================================================
    @PUT
    @Path("/{id}/perbaiki")
    @Transactional
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    public Response perbaikiData(
            @PathParam("id") Long id,
            @HeaderParam("userId") Long userId,
            EditForm form
    ) {
        PendaftaranMudik p = PendaftaranMudik.findById(id);
        if (p == null) return Response.status(404).build();

        if (!p.user.user_id.equals(userId)) return Response.status(403).build();

        // Validasi Status (Hanya DITOLAK yang boleh diedit)
        if (!"DITOLAK".equals(p.status_pendaftaran)) {
            return Response.status(400).entity(Map.of("error", "Hanya data DITOLAK yang bisa diperbaiki")).build();
        }

        // Cek Kuota (Ambil lagi)
        if (p.rute.getSisaKuota() <= 0) {
            return Response.status(400).entity(Map.of("error", "Maaf, kuota rute sudah habis.")).build();
        }

        // Update Data
        if (form.nama_peserta != null) p.nama_peserta = form.nama_peserta.toUpperCase();
        if (form.nik_peserta != null) p.nik_peserta = form.nik_peserta;
        if (form.jenis_kelamin != null) p.jenis_kelamin = form.jenis_kelamin;
        if (form.tanggal_lahir != null) p.tanggal_lahir = LocalDate.parse(form.tanggal_lahir);

        if (form.fotoBukti != null && form.fotoBukti.fileName() != null) {
            p.foto_identitas_path = simpanFile(form.fotoBukti, p.nik_peserta);
        }

        // Ubah Status Jadi MENUNGGU & AMBIL KUOTA LAGI
        p.status_pendaftaran = "MENUNGGU_VERIFIKASI";
        p.rute.kuota_terisi += 1;

        p.rute.persist();
        p.persist();

        return Response.ok(Map.of("status", "BERHASIL", "pesan", "Data diperbaiki")).build();
    }

    // ==========================================================
    // 4. CEK BY UUID (POIN 7 - LINK AMAN)
    // ==========================================================
    @GET
    @Path("/uuid/{uuid}")
    public Response getByUuid(@PathParam("uuid") String uuid) {
        return PendaftaranMudik.find("uuid", uuid).firstResultOptional()
                .map(p -> Response.ok(p).build())
                .orElse(Response.status(404).build());
    }

    // ==========================================================
    // 5. FILE SERVING (ABSOLUTE PATH BIAR GAK 404)
    // ==========================================================
    @GET
    @Path("/uploads/{filename}")
    @Produces({"image/jpeg", "image/png", "application/pdf"})
    public Response getFile(@PathParam("filename") String filename) {
        File file = new File(UPLOAD_DIR + filename);
        if (!file.exists()) return Response.status(404).build();
        return Response.ok(file).build();
    }

    // ==========================================================
    // HELPER: SIMPAN FILE (ABSOLUTE PATH)
    // ==========================================================
    private String simpanFile(FileUpload fileUpload, String nik) {
        try {
            File folder = new File(UPLOAD_DIR);
            if (!folder.exists()) folder.mkdirs();

            String originalName = fileUpload.fileName();
            String ext = originalName.contains(".") ? originalName.substring(originalName.lastIndexOf(".")) : ".jpg";

            // Pake UUID biar nama file gak bentrok
            String newName = "KTP-" + nik + "-" + UUID.randomUUID().toString().substring(0, 8) + ext;

            File dest = new File(folder, newName);
            Files.move(fileUpload.uploadedFile(), dest.toPath(), StandardCopyOption.REPLACE_EXISTING);

            // Return relative string "uploads/namafile.jpg" buat disimpan di DB
            return "uploads/" + newName;
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }
}