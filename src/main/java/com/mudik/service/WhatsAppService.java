package com.mudik.service;

import com.mudik.model.PendaftaranMudik;
import jakarta.enterprise.context.ApplicationScoped;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@ApplicationScoped
public class WhatsAppService {

    // --- TEMPLATE PESAN (Bisa diganti sesuka hati tanpa ganggu AdminResource) ---
    private static final String TEMPLATE_TERIMA =
            "Selamat Pagi/Siang/Sore Bapak/Ibu *%s*,\n\n" +
                    "Kami dari Panitia Mudik Gratis menginformasikan bahwa pendaftaran Anda telah *DITERIMA*.\n" +
                    "Rute: %s\n" +
                    "Bus: %s\n\n" +
                    "Mohon tunggu informasi selanjutnya untuk jadwal keberangkatan.";

    private static final String TEMPLATE_TOLAK =
            "Mohon Maaf Bapak/Ibu *%s*,\n\n" +
                    "Pendaftaran Mudik Gratis Anda *DITOLAK* dikarenakan data tidak valid atau kuota telah penuh.\n" +
                    "Silakan coba lagi di kesempatan berikutnya.";

    private static final String TEMPLATE_VERIF =
            "Halo Bapak/Ibu *%s*,\n\n" +
                    "Data pendaftaran Mudik Gratis Anda saat ini sedang dalam tahap *VERIFIKASI* oleh tim kami.\n" +
                    "Mohon kesediaannya menunggu update status selanjutnya.";

    // --- FUNGSI GENERATOR LINK ---
    public String generateLink(String noHp, String tipe, PendaftaranMudik p) {
        if (noHp == null || noHp.length() < 7) return "#";

        // Format HP (08xx -> 628xx)
        String hpFormat = noHp.replaceAll("[^0-9]", "");
        if (hpFormat.startsWith("0")) hpFormat = "62" + hpFormat.substring(1);

        // Ambil Data
        String nama = p.nama_peserta;
        String rute = (p.rute != null) ? p.rute.tujuan : "Aceh";
        String bus = (p.kendaraan != null) ? p.kendaraan.nama_armada : "Informasi Menyusul";

        // Pilih Template
        String pesan = "";
        switch (tipe) {
            case "TERIMA":
                pesan = String.format(TEMPLATE_TERIMA, nama, rute, bus);
                break;
            case "TOLAK_DATA":
                pesan = String.format(TEMPLATE_TOLAK, nama);
                break;
            default: // VERIFIKASI
                pesan = String.format(TEMPLATE_VERIF, nama);
                break;
        }

        // Encode ke URL WA
        try {
            return "https://wa.me/" + hpFormat + "?text=" + URLEncoder.encode(pesan, StandardCharsets.UTF_8);
        } catch (Exception e) { return "#"; }
    }
}