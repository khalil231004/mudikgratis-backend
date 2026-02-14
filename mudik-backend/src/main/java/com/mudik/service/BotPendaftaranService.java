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
            List<String> listJenisBarang,
            List<String> listUkuran,
            List<String> listAlamat,
            List<String> listNoHp
    ) {

        User user = User.findById(userId);
        if (user == null) throw new IllegalArgumentException("User ID tidak ditemukan!");

        Rute rute = Rute.findById(ruteId);
        if (rute == null) throw new IllegalArgumentException("Rute tidak ditemukan!");

        int jumlah = listNama.size();

        // Validasi konsistensi array
        if (listNik.size() != jumlah || listGender.size() != jumlah) {
            throw new IllegalArgumentException("Data tidak konsisten! Jumlah NIK dan Nama tidak sama.");
        }

        // --- CEK KUOTA GLOBAL (TRIPLE KUOTA LOGIC) ---
        // Hitung dulu sisa kursi real (Total - Terisi)
        int sisaKursi = rute.kuota_total - (rute.kuota_terisi != null ? rute.kuota_terisi : 0);

        // Kita hitung berapa kursi yang dibutuhkan (Bayi tidak dihitung)
        int kursiDibutuhkan = 0;
        for (int i = 0; i < jumlah; i++) {
            LocalDate tglLahir = LocalDate.parse(listTglLahir.get(i));
            int umur = Period.between(tglLahir, LocalDate.now()).getYears();
            if (umur >= 2) {
                kursiDibutuhkan++;
            }
        }

        if (sisaKursi < kursiDibutuhkan) {
            throw new IllegalArgumentException("Mohon maaf, kursi bus habis! Sisa kursi: " + sisaKursi);
        }

        // --- PROSES SIMPAN ---
        for (int i = 0; i < jumlah; i++) {
            String nik = listNik.get(i);

            // Cek NIK Duplikat
            if (PendaftaranMudik.count("nik_peserta", nik) > 0) {
                throw new IllegalArgumentException("NIK " + nik + " sudah terdaftar!");
            }

            LocalDate tglLahir = LocalDate.parse(listTglLahir.get(i));
            int umur = Period.between(tglLahir, LocalDate.now()).getYears();
            String kategori = (umur < 2) ? "BAYI" : (umur < 5) ? "ANAK" : "DEWASA";

            PendaftaranMudik p = new PendaftaranMudik();
            p.user = user;
            p.rute = rute;
            p.nama_peserta = listNama.get(i).toUpperCase();
            p.nik_peserta = nik;
            p.jenis_kelamin = listGender.get(i);
            p.tanggal_lahir = tglLahir;
            p.kategori_penumpang = kategori;

            // Handle optional lists (biar gak IndexOutOfBounds kalau list kosong/null)
            if (listIdentitas != null && listIdentitas.size() > i) p.jenis_identitas = listIdentitas.get(i);
            if (listPathBukti != null && listPathBukti.size() > i) p.foto_identitas_path = listPathBukti.get(i);
            if (listJenisBarang != null && listJenisBarang.size() > i) p.jenis_barang = listJenisBarang.get(i);
            if (listUkuran != null && listUkuran.size() > i) p.ukuran_barang = listUkuran.get(i);
            if (listAlamat != null && listAlamat.size() > i) p.alamat_rumah = listAlamat.get(i);

            // Logic HP
            String hpInput = null;
            if (listNoHp != null && listNoHp.size() > i) hpInput = listNoHp.get(i);
            p.no_hp_peserta = (hpInput != null && !hpInput.isBlank()) ? hpInput : user.no_hp;

            p.status_pendaftaran = "MENUNGGU_VERIFIKASI";
            p.kode_booking = "MDK-" + ruteId + "-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase();

            p.persist();
        }

        // --- UPDATE KUOTA (TRIPLE KUOTA LOGIC) ---
        // Kita nambahin counter Terisi, BUKAN ngurangin sisa
        if (rute.kuota_terisi == null) rute.kuota_terisi = 0;
        rute.kuota_terisi += kursiDibutuhkan;

        // rute.persist() tidak perlu dipanggil karena @Transactional otomatis update dirty entity
    }
}