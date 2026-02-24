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

        // Validasi konsistensi array (Wajib sama panjang)
        if (listNik.size() != jumlah || listGender.size() != jumlah || listTglLahir.size() != jumlah) {
            throw new IllegalArgumentException("Data tidak konsisten! Jumlah NIK, Nama, dan Tgl Lahir harus sama.");
        }

        // --- CEK KUOTA GLOBAL (TRIPLE KUOTA LOGIC) ---
        // Hitung dulu sisa kursi real (Total - Terisi)
        int sisaKursi = rute.kuota_total - (rute.kuota_terisi != null ? rute.kuota_terisi : 0);

        // Kita hitung berapa kursi yang dibutuhkan (Bayi tidak dihitung)
        int kursiDibutuhkan = 0;
        for (int i = 0; i < jumlah; i++) {
            try {
                LocalDate tglLahir = LocalDate.parse(listTglLahir.get(i));
                int umur = Period.between(tglLahir, LocalDate.now()).getYears();
                if (umur >= 2) { // Logic: Dibawah 2 tahun (BAYI) pangku, gak makan kuota
                    kursiDibutuhkan++;
                }
            } catch (Exception e) {
                // Fallback kalau tanggal error, anggap Dewasa (makan kursi)
                kursiDibutuhkan++;
            }
        }

        if (sisaKursi < kursiDibutuhkan) {
            throw new IllegalArgumentException("Mohon maaf, kursi bus habis! Sisa kursi: " + sisaKursi);
        }

        // --- PROSES SIMPAN ---
        for (int i = 0; i < jumlah; i++) {
            String nik = listNik.get(i).trim();

            // 🔥 FIX LOGIC NIK: Boleh daftar lagi kalau status sebelumnya DITOLAK/DIBATALKAN
            // (Kode lama lu ngeblok semua NIK tanpa ampun)
            long cekNik = PendaftaranMudik.count("nik_peserta = ?1 AND status_pendaftaran NOT IN ('DIBATALKAN', 'DITOLAK')", nik);
            if (cekNik > 0) {
                throw new IllegalArgumentException("NIK " + nik + " sudah terdaftar & aktif! Cek status pendaftaran.");
            }

            LocalDate tglLahir;
            String kategori;
            try {
                tglLahir = LocalDate.parse(listTglLahir.get(i));
                int umur = Period.between(tglLahir, LocalDate.now()).getYears();
                // FIX: Gunakan "ANAK-ANAK" (konsisten frontend), batas anak 2-16 tahun
                kategori = (umur < 2) ? "BAYI" : (umur < 17) ? "ANAK-ANAK" : "DEWASA";
            } catch (Exception e) {
                tglLahir = LocalDate.now();
                kategori = "DEWASA";
            }

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
            if (listAlamat != null && listAlamat.size() > i) p.alamat_rumah = listAlamat.get(i);

            // Logic HP
            String hpInput = null;
            if (listNoHp != null && listNoHp.size() > i) hpInput = listNoHp.get(i);
            p.no_hp_peserta = (hpInput != null && !hpInput.isBlank()) ? hpInput : user.no_hp;

            // 🔥 FIX STATUS TYPO: PAKE SPASI! (JANGAN PAKE UNDERSCORE)
            // Biar kebaca sama filter Admin "MENUNGGU VERIFIKASI"
            p.status_pendaftaran = "MENUNGGU VERIFIKASI";

            p.kode_booking = "MDK-" + ruteId + "-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase();

            p.persist();
        }

        // --- UPDATE KUOTA ---
        if (rute.kuota_terisi == null) rute.kuota_terisi = 0;
        rute.kuota_terisi += kursiDibutuhkan;

        // Data tersimpan otomatis karena @Transactional
    }
}