package com.mudik.service;

import com.mudik.model.PendaftaranMudik;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@ApplicationScoped
public class WhatsAppService {

    @ConfigProperty(name = "app.frontend.url")
    String frontendUrl;

    /**
     * Generate Link WhatsApp dengan Template Profesional Dishub Aceh
     */
    public String generateLink(String noHp, String tipe, PendaftaranMudik p, String alasan) {
        // 1. Validasi Nomor HP
        if (noHp == null || noHp.length() < 7) return "#";

        // 2. Format HP (08xx -> 628xx)
        String hpFormat = noHp.replaceAll("[^0-9]", "");
        if (hpFormat.startsWith("0")) hpFormat = "62" + hpFormat.substring(1);

        String pesan = "";
        String linkAction = "";
        String footer = "\n\n_Pesan otomatis dari Sistem Mudik Gratis - Dinas Perhubungan Aceh_";

        try {
            switch (tipe) {
                // KASUS 1: LOLOS VERIFIKASI AWAL
                case "TERIMA":
                    pesan = "👋 *Salam Seulamat dari Dishub Aceh*\n\n" +
                            "Halo Sdr/i *" + p.nama_peserta + "*,\n" +
                            "✅ *PENDAFTARAN BERHASIL DI-VERIFIKASI*\n\n" +
                            "Selamat! Data administrasi Anda telah kami terima dan dinyatakan *LENGKAP*.\n" +
                            "🎫 Kode Booking: *" + p.kode_booking + "*\n\n" +
                            "Langkah selanjutnya: Mohon pantau terus Dashboard untuk informasi jadwal bus dan tiket elektronik Anda di sini:";
                    linkAction = frontendUrl + "/login";
                    break;

                // KASUS 2: DITOLAK (DATA SALAH/BURAM)
                case "TOLAK_DATA":
                    String alasanFinal = (alasan != null && !alasan.isBlank()) ? alasan : "Data tidak terbaca/tidak sesuai syarat.";
                    pesan = "👋 *Salam Seulamat dari Dishub Aceh*\n\n" +
                            "Halo Sdr/i *" + p.nama_peserta + "*,\n" +
                            "❌ *MOHON MAAF, PERLU PERBAIKAN DATA*\n\n" +
                            "Verifikasi pendaftaran Anda belum lolos dikarenakan:\n" +
                            "👉 *" + alasanFinal + "*\n\n" +
                            "Jangan khawatir! Anda masih memiliki kesempatan untuk memperbaiki data (Upload Ulang) melalui link di bawah ini:";
                    linkAction = frontendUrl + "/login";
                    break;

                // KASUS 3: KONFIRMASI KEHADIRAN (H-3)
                case "DITERIMA(H-3)":
                    pesan = "👋 *Salam Seulamat dari Dishub Aceh*\n\n" +
                            "Halo Sdr/i *" + p.nama_peserta + "*,\n" +
                            "🚍 *PENTING: KONFIRMASI KEBERANGKATAN*\n\n" +
                            "Jadwal mudik sudah dekat! Kami membutuhkan kepastian kehadiran Anda untuk penyiapan kursi bus.\n\n" +
                            "⚠️ *Wajib Klik Link Ini Sekarang:*\n" +
                            "(Jika tidak dikonfirmasi, kursi akan dialihkan ke peserta lain)";
                    linkAction = frontendUrl + "/konfirmasi/" + p.uuid;
                    break;

                // DEFAULT
                default:
                    pesan = "👋 *Halo dari Dishub Aceh*\n\nInformasi terkait pendaftaran Mudik Gratis Anda.";
                    linkAction = frontendUrl;
                    break;
            }

            // Gabungkan Pesan + Link + Footer
            String fullMessage = pesan + "\n" + linkAction + footer;

            // Encode URL agar karakter spasi/enter terbaca di WA
            return "https://wa.me/" + hpFormat + "?text=" + URLEncoder.encode(fullMessage, StandardCharsets.UTF_8.toString());

        } catch (Exception e) {
            System.err.println("Gagal generate link WA: " + e.getMessage());
            return "#";
        }
    }
}