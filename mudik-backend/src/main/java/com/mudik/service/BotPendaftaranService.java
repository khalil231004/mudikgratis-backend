package com.mudik.service;

import com.mudik.model.*;
import io.quarkus.mailer.Mailer;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.time.LocalDate;
import java.time.Period;
import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class BotPendaftaranService {

    @Inject
    Mailer mailer;

    @Transactional
    public void prosesPendaftaranBatch(
            Long userId, Long ruteId,
            List<String> listNama, List<String> listNik, List<String> listGender,
            List<String> listTglLahir, List<String> listIdentitas,
            List<String> listPathBukti,
            List<String> listJenisBarang, // UBAH: Double -> String
            List<String> listUkuran,
            List<String> listAlamat,      // UBAH: TitikJemput -> AlamatRumah
            List<String> listNoHp
    ) {

        User user = User.findById(userId);
        if (user == null) throw new IllegalArgumentException("User ID tidak ditemukan!");

        Rute rute = Rute.findById(ruteId);
        if (rute == null) throw new IllegalArgumentException("Rute tidak ditemukan!");

        int jumlah = listNama.size();

        // Validasi konsistensi array dasar
        if (listNik.size() != jumlah || listGender.size() != jumlah || listPathBukti.size() != jumlah) {
            throw new IllegalArgumentException("Data tidak konsisten! Pastikan semua kolom terisi untuk " + jumlah + " peserta.");
        }

        // --- REVISI KUOTA (MAX 6) ---
        long sudahDaftar = PendaftaranMudik.count("user.user_id", userId);
        if ((sudahDaftar + jumlah) > 6) {
            throw new IllegalArgumentException("Gagal! Kuota habis. Anda sisa: " + (6 - sudahDaftar) + " slot (Max 6/akun).");
        }

        int kursiDibutuhkan = 0;

        for (int i = 0; i < jumlah; i++) {
            String nik = listNik.get(i);

            // Cek NIK (mencegah duplikat NIK yang sama di DB)
            if (PendaftaranMudik.count("nik_peserta", nik) > 0) {
                throw new IllegalArgumentException("NIK " + nik + " sudah terdaftar!");
            }

            LocalDate tglLahir = LocalDate.parse(listTglLahir.get(i));
            int umur = Period.between(tglLahir, LocalDate.now()).getYears();
            String kategori = (umur < 2) ? "BAYI" : (umur < 5) ? "ANAK" : "DEWASA";

            // Bayi tidak makan kuota kursi bus
            if (!kategori.equals("BAYI")) kursiDibutuhkan++;

            PendaftaranMudik p = new PendaftaranMudik();
            p.user = user;
            p.rute = rute;
            p.nama_peserta = listNama.get(i).toUpperCase();
            p.nik_peserta = nik;
            p.jenis_kelamin = listGender.get(i);
            p.tanggal_lahir = tglLahir;
            p.kategori_penumpang = kategori;

            p.jenis_identitas = listIdentitas.get(i);
            p.foto_identitas_path = listPathBukti.get(i);

            // REVISI BARANG & ALAMAT
            p.jenis_barang = listJenisBarang.get(i); // Isi String (Kardus/Koper)
            p.ukuran_barang = listUkuran.get(i);
            p.alamat_rumah = listAlamat.get(i);      // Isi Alamat Rumah

            // --- REVISI LOGIKA NO HP (AUTO FILL OWNER) ---
            String hpInput = null;
            if (listNoHp != null && listNoHp.size() > i) {
                hpInput = listNoHp.get(i);
            }

            if (hpInput == null || hpInput.trim().isEmpty()) {
                // Jika kosong, PAKAI HP PEMILIK AKUN
                // Pastikan entity User punya kolom no_hp, atau ganti dengan getter yang sesuai
                p.no_hp_peserta = user.no_hp;
            } else {
                p.no_hp_peserta = hpInput;
            }
            // --------------------------------------------

            p.status_pendaftaran = "MENUNGGU_VERIFIKASI";
            p.kode_booking = "MDK-" + ruteId + "-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase();

            p.persist();
        }

        // Cek Kuota Bus Global
        if (rute.kuota_tersisa < kursiDibutuhkan) {
            throw new IllegalArgumentException("Mohon maaf, kursi bus habis! Sisa kursi: " + rute.kuota_tersisa);
        }
        rute.kuota_tersisa -= kursiDibutuhkan;

        // Opsional: Kirim Email/WA Notifikasi di sini
        // mailer.send(...)
    }
}