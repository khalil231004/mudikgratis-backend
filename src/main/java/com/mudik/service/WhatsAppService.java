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
    Optional<String> frontendUrlOpt; // Pakai Optional biar gak error kalau lupa set di properties

    public String generateLink(String noHp, String tipe, PendaftaranMudik p, String alasan) {
        if (noHp == null || noHp.length() < 7) return "#";

        String hpFormat = noHp.replaceAll("[^0-9]", "");
        if (hpFormat.startsWith("0")) hpFormat = "62" + hpFormat.substring(1);

        // Default URL ke Login jika config kosong
        String baseUrl = frontendUrlOpt.orElse("https://dishubosrm.acehprov.go.id");

        String pesan = "";
        String linkAction = "";
        String footer = "\n\n_Pesan otomatis Sistem Mudik Gratis Dishub Aceh_";

        try {
            switch (tipe) {
                // KASUS 1: DITOLAK / PERLU REVISI (Prioritas Utama sesuai request)
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

                // KASUS 2: DITERIMA & KONFIRMASI H-3
                case "DITERIMA(H-3)":
                    pesan = "👋 *Salam Seulamat dari Dishub Aceh*\n\n" +
                            "Yth. Sdr/i *" + p.nama_peserta + "*,\n" +
                            "Selamat! Pendaftaran Mudik Anda telah *DITERIMA*.\n\n" +
                            "🚍 *PENTING: KONFIRMASI KEBERANGKATAN*\n" +
                            "Agar kursi Anda tidak hangus, WAJIB melakukan konfirmasi kehadiran melalui link ini:";
                    // Mengarah ke halaman konfirmasi khusus
                    linkAction = baseUrl + "/konfirmasi/" + p.uuid;
                    break;

                // KASUS 3: TERIMA AWAL
                default:
                    pesan = "👋 *Salam Seulamat dari Dishub Aceh*\n\n" +
                            "Halo Sdr/i *" + p.nama_peserta + "*,\n" +
                            "Data pendaftaran Anda telah kami terima. Pantau terus status tiket Anda di Dashboard.";
                    linkAction = baseUrl + "/login";
                    break;
            }

            String fullMessage = pesan + "\n" + linkAction + footer;
            return "https://wa.me/" + hpFormat + "?text=" + URLEncoder.encode(fullMessage, StandardCharsets.UTF_8.toString());

        } catch (Exception e) {
            System.err.println("Error WA: " + e.getMessage());
            return "#";
        }
    }
}