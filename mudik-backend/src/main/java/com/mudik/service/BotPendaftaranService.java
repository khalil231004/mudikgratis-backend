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
            List<Double> listBerat, List<String> listUkuran,
            List<String> titik_jemput,
            List<String> listNoHp
    ) {
        // 1. Validasi Awal
        User user = User.findById(userId);
        if (user == null) throw new IllegalArgumentException("User ID tidak ditemukan!");

        Rute rute = Rute.findById(ruteId);
        if (rute == null) throw new IllegalArgumentException("Rute tidak ditemukan!");

        // --- 🛡️ VALIDASI JUMLAH DATA (WAJIB SAMA SEMUA) ---
        int jumlah = listNama.size();
        if (listNik.size() != jumlah || listGender.size() != jumlah || listPathBukti.size() != jumlah) {
            throw new IllegalArgumentException("Data tidak konsisten! Pastikan semua kolom terisi untuk " + jumlah + " peserta.");
        }

        // 2. CEK LIMIT AKUN
        long sudahDaftar = PendaftaranMudik.count("user.user_id", userId);
        if ((sudahDaftar + jumlah) > 5) {
            throw new IllegalArgumentException("Gagal! Sisa kuota akun Anda hanya: " + (5 - sudahDaftar));
        }

        // 3. LOOPING
        int kursiDibutuhkan = 0;

        for (int i = 0; i < jumlah; i++) {
            String nik = listNik.get(i);

            // Cek NIK
            if (PendaftaranMudik.count("nik_peserta", nik) > 0) {
                throw new IllegalArgumentException("NIK " + nik + " sudah terdaftar!");
            }

            // Hitung Umur
            LocalDate tglLahir = LocalDate.parse(listTglLahir.get(i));
            int umur = Period.between(tglLahir, LocalDate.now()).getYears();
            String kategori = (umur < 2) ? "BAYI" : (umur < 5) ? "ANAK" : "DEWASA";

            if (!kategori.equals("BAYI")) kursiDibutuhkan++;

            // Simpan
            PendaftaranMudik p = new PendaftaranMudik();
            p.user = user;
            p.rute = rute;
            p.nama_peserta = listNama.get(i).toUpperCase();
            p.nik_peserta = nik;
            p.jenis_kelamin = listGender.get(i);
            p.tanggal_lahir = tglLahir;
            p.kategori_penumpang = kategori;

            p.jenis_identitas = listIdentitas.get(i);
            p.foto_identitas_path = listPathBukti.get(i); // Ini aman karena udah divalidasi size-nya di Resource

            p.berat_barang = listBerat.get(i);
            p.ukuran_barang = listUkuran.get(i);
            p.titik_jemput = titik_jemput.get(i);

            // --- 🔥 FIX LOGIC NO HP ---
            // Kita paksa ambil index i. Kalau frontend gak ngirim string kosong, salah mereka.
            // Tapi kita jagain biar gak crash index out of bounds.
            if (listNoHp != null && listNoHp.size() == jumlah) {
                String hp = listNoHp.get(i);
                // Cuma simpan kalau isinya angka valid (bukan string kosong)
                if (hp != null && hp.length() > 5) {
                    p.no_hp_peserta = hp;
                }
            }
            // Kalau listNoHp ukurannya beda sama jumlah peserta, kita abaikan semua no hp (daripada ketuker)

            p.status_pendaftaran = "MENUNGGU_VERIFIKASI";
            p.kode_booking = "MDK-" + ruteId + "-" + UUID.randomUUID().toString().substring(0,6).toUpperCase();

            p.persist();
        }

        // 4. CEK KUOTA BUS
        if (rute.kuota_tersisa < kursiDibutuhkan) {
            throw new IllegalArgumentException("Kuota Bus Habis! Sisa: " + rute.kuota_tersisa);
        }
        rute.kuota_tersisa -= kursiDibutuhkan;
    }
}