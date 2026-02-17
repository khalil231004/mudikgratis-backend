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
        String footer = "\n\n_Pesan otomatis Sistem Mudik Gratis Dishub Aceh_";

        try {
            switch (tipe) {
                case "TOLAK_DATA":
                    String alasanFinal = (alasan != null && !alasan.isBlank()) ? alasan : "Data belum lengkap.";
                    pesan = "👋 *Salam Seulamat dari Dishub Aceh*\n\n" +
                            "Yth. Sdr/i *" + p.nama_peserta + "*,\n" +
                            "Mohon maaf, Verifikasi Pendaftaran Mudik Anda *BELUM LENGKAP*.\n\n" +
                            "Catatan Petugas:\n" +
                            "👉 *" + alasanFinal + "*\n\n" +
                            "Mohon segera perbaiki data Anda melalui link dashboard berikut:";
                    linkAction = baseUrl + "/login";
                    break;

                case "DITERIMA(H-3)":
                    pesan = "👋 *Salam Seulamat dari Dishub Aceh*\n\n" +
                            "Yth. Sdr/i *" + p.nama_peserta + "*,\n" +
                            "Selamat! Pendaftaran Mudik Anda telah *DITERIMA*.\n\n" +
                            "🚍 *PENTING: KONFIRMASI KEBERANGKATAN*\n" +
                            "Agar kursi Anda tidak hangus, WAJIB melakukan konfirmasi kehadiran melalui link ini:";
                    linkAction = baseUrl + "/konfirmasi/" + p.uuid;
                    break;

                default: // TERIMA
                    pesan = "👋 *Salam Seulamat dari Dishub Aceh*\n\n" +
                            "Halo Sdr/i *" + p.nama_peserta + "*,\n" +
                            "Data pendaftaran Anda telah kami terima. Pantau terus status tiket Anda di Dashboard.";
                    linkAction = baseUrl + "/login";
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
}