package com.mudik.service;

import com.mudik.model.*;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import java.time.LocalDateTime;

@ApplicationScoped
public class BotPendaftaranService {

    @Transactional
    // Pastikan baris ini persis seperti di bawah:
    public PendaftaranMudik prosesPendaftaran(
            Long userId,
            Long routeId,
            String nikPesertaInput,
            String namaPesertaInput,
            String pathKtp,
            String pathKk,
            String pathBarang, // <-- Ini yang baru
            String hubungan    // <-- JANGAN LUPA INI (Penyebab Error kamu)
    ) {
        // ... isi kodingan ke bawah aman ...{

        // 1. VALIDASI DASAR
        User user = User.findById(userId);
        Rute rute = Rute.findById(routeId);

        if (user == null) throw new IllegalArgumentException("User tidak ditemukan.");
        if (rute == null) throw new IllegalArgumentException("Rute tidak ditemukan.");

        if (!nikPesertaInput.matches("\\d+") || nikPesertaInput.length() != 16) {
            throw new IllegalArgumentException("NIK harus 16 digit angka!");
        }

        if (namaPesertaInput == null || namaPesertaInput.trim().isEmpty()) {
            throw new IllegalArgumentException("Nama Peserta wajib diisi!");
        }

        // 2. CEK DUPLIKASI
        long cekNik = PendaftaranMudik.count("nik_peserta = ?1 AND (status_pendaftaran = 'MENUNGGU_VERIFIKASI' OR status_pendaftaran = 'DITERIMA')", nikPesertaInput);
        if (cekNik > 0) throw new IllegalArgumentException("NIK " + nikPesertaInput + " sudah terdaftar!");

        long cekKtp = PendaftaranMudik.count("foto_ktp_path = ?1", pathKtp);
        if (cekKtp > 0) throw new IllegalArgumentException("Foto KTP ini sudah digunakan!");


        // 4. SIMPAN DATA
        PendaftaranMudik p = new PendaftaranMudik();
        p.user = user;
        p.rute = rute;
        p.nik_peserta = nikPesertaInput;
        p.nama_peserta = namaPesertaInput.toUpperCase();
        p.hubungan_keluarga = (hubungan != null && !hubungan.isEmpty()) ? hubungan : "DIRI SENDIRI";

        p.foto_ktp_path = pathKtp;
        p.foto_kk_path = pathKk;
        p.foto_barang_path = pathBarang; // Simpan Path Barang

        p.status_pendaftaran = "MENUNGGU_VERIFIKASI";
        p.bot_flag = "AMAN";
        p.created_at = LocalDateTime.now();

        // PENTING: Simpan dulu biar dapet ID
        p.persist();

        String rawTujuan = rute.tujuan.toUpperCase().replaceAll("[^A-Z]", "");

        // 2. Ambil 3 Huruf Pertama sebagai Kode
        // Kalau nama kotanya pende{
        //  "error": "SRJWT05009: "
        //}k (misal: "Ie"), ambil semuanya.
        String kodeKota = (rawTujuan.length() >= 3) ? rawTujuan.substring(0, 3) : rawTujuan;

        // 3. Gabungkan Jadi Token
        // Format: [KODE_KOTA]-[ID_RUTE]-[ID_PENDAFTARAN]
        // Contoh: SIG-5-101 atau BAN-1-205
        p.kode_token_barang = kodeKota + "-" + routeId + "-" + p.pendaftaran_id;
        return p;
    }
}