package com.mudik.service;

import com.mudik.model.PendaftaranMudik;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

@ApplicationScoped
public class WhatsAppService {

    @ConfigProperty(name = "app.frontend.url")
    Optional<String> frontendUrlOpt;

    public String generateLink(String noHp, String tipe, PendaftaranMudik p, String alasan) {
        // 🔥 DEBUG LOG 1: Cek Input yang masuk
        System.out.println("========== DEBUG WA ==========");
        System.out.println("No HP: " + noHp);
        System.out.println("Tipe: " + tipe);
        System.out.println("Nama: " + p.nama_peserta);

        // 1. Cek Validasi Nomor HP
        if (noHp == null || noHp.length() < 7) {
            System.out.println("❌ ERROR WA: Nomor HP Kosong atau Tidak Valid!");
            return "#";
        }

        String hpFormat = noHp.replaceAll("[^0-9]", "");
        if (hpFormat.startsWith("0")) hpFormat = "62" + hpFormat.substring(1);

        // 2. Cek Config URL Frontend
        String baseUrl = frontendUrlOpt.orElse("https://dishubosrm.acehprov.go.id");
        System.out.println("Base URL: " + baseUrl);

        String pesan = "";
        String linkAction = "";
        String hotline = "📞 *Hotline Mudik Gratis Dishub Aceh:*\n08217653093 (WhatsApp)";
        String footer = "\n\n" + hotline + "\n\n_Pesan otomatis Sistem Mudik Gratis Dishub Aceh_";

        try {
            switch (tipe) {
                case "TOLAK_DATA":
                    // ✅ Batas perbaikan 1 jam DIHAPUS atas permintaan klien
                    String alasanFinal = (alasan != null && !alasan.isBlank()) ? alasan : "Data belum lengkap.";
                    pesan = "👋 *Salam Seulamat dari Dishub Aceh*\n\n" +
                            "Yth. Sdr/i *" + p.nama_peserta + "*,\n" +
                            "Mohon maaf, Verifikasi Pendaftaran Mudik Anda *BELUM LENGKAP*.\n\n" +
                            "Catatan Petugas:\n" +
                            "👉 *" + alasanFinal + "*\n\n" +
                            "Silakan login dan perbaiki data Anda di:\n" + baseUrl;
                    break;

                case "DITERIMA(H-3)":
                    pesan = "👋 *Salam Seulamat dari Dishub Aceh*\n\n" +
                            "Yth. Sdr/i *" + p.nama_peserta + "*,\n" +
                            "Selamat! Pendaftaran Mudik Anda telah *DITERIMA*.\n\n" +
                            "🚍 *PENTING: KONFIRMASI KEBERANGKATAN*\n" +
                            "Agar kursi Anda tidak hangus, WAJIB melakukan konfirmasi kehadiran " +
                            "yang akan *dikirimkan admin* sesuai jadwal. " +
                            "Nanti Anda bisa konfirmasi langsung di *Dashboard* aplikasi Seulamat maupun via link WA yang akan kami kirimkan.\n\n" +
                            "Pantau terus status tiket Anda di Dashboard aplikasi Seulamat.";
                    break;

                default: // TERIMA
                    pesan = "👋 *Salam Seulamat dari Dishub Aceh*\n\n" +
                            "Yth. Sdr/i *" + p.nama_peserta + "*,\n" +
                            "Selamat! Pendaftaran Mudik Anda telah *DITERIMA*.\n\n" +
                            "🚍 *PENTING: KONFIRMASI KEBERANGKATAN*\n" +
                            "Agar kursi Anda tidak hangus, WAJIB melakukan konfirmasi kehadiran " +
                            "yang akan *dikirimkan admin* sesuai jadwal. " +
                            "Nanti Anda bisa konfirmasi langsung di *Dashboard* aplikasi Seulamat maupun via link WA yang akan kami kirimkan.\n\n" +
                            "Pantau terus status tiket Anda di Dashboard aplikasi Seulamat.";
                    break;
            }

            String fullMessage = pesan + "\n" + linkAction + footer;
            String finalLink = "https://wa.me/" + hpFormat + "?text=" + URLEncoder.encode(fullMessage, StandardCharsets.UTF_8.toString());

            System.out.println("✅ SUKSES GENERATE LINK: " + finalLink);
            return finalLink;

        } catch (Exception e) {
            System.err.println("❌ CRITICAL ERROR WA: " + e.getMessage());
            e.printStackTrace();
            return "#";
        }
    }

    // FIX 10: Notifikasi kuota penuh ke penumpang
    public String generateKuotaPenuhLink(String noHp, String namaPeserta, String namaRute) {
        if (noHp == null || noHp.length() < 7) return "#";
        String hpFormat = noHp.replaceAll("[^0-9]", "");
        if (hpFormat.startsWith("0")) hpFormat = "62" + hpFormat.substring(1);

        String hotline = "📞 *Hotline:* 08217653093";
        try {
            String pesan = "👋 *Salam Seulamat dari Dishub Aceh*\n\n" +
                    "Yth. Sdr/i *" + namaPeserta + "*,\n" +
                    "Kami informasikan bahwa *KUOTA RUTE " + namaRute.toUpperCase() + " TELAH PENUH*.\n\n" +
                    "Mohon maaf atas ketidaknyamanannya. Pantau terus dashboard untuk informasi ketersediaan kursi.\n\n" +
                    hotline + "\n\n" +
                    "Pesan otomatis Sistem Mudik Gratis Dishub Aceh";
            return "https://wa.me/" + hpFormat + "?text=" + URLEncoder.encode(pesan, StandardCharsets.UTF_8.toString());
        } catch (Exception e) {
            return "#";
        }
    }
}