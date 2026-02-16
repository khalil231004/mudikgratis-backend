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
     * @param noHp Nomor tujuan
     * @param tipe Tipe pesan (TERIMA, TOLAK_DATA, DITERIMA(H-3))
     * @param p Objek pendaftaran
     * @param alasan Alasan penolakan (diisi jika tipe adalah TOLAK_DATA)
     */
    public String generateLink(String noHp, String tipe, PendaftaranMudik p, String alasan) {
        if (noHp == null || noHp.length() < 7) return "#";

        String hpFormat = noHp.replaceAll("[^0-9]", "");
        if (hpFormat.startsWith("0")) hpFormat = "62" + hpFormat.substring(1);

        String pesan = "";
        String linkAction = "";

        try {
            switch (tipe) {
                case "TERIMA":
                    pesan = "Halo Sdr/i *" + p.nama_peserta + "*,\n\n" +
                            "Selamat! Pendaftaran Mudik Gratis Anda telah *DITERIMA*.\n" +
                            "Kode Booking: *" + p.kode_booking + "*\n\n" +
                            "Silakan cek tiket Anda di sini: ";
                    linkAction = frontendUrl + "/status/" + p.uuid;
                    break;

                case "TOLAK_DATA":
                    // Menggunakan variabel alasan yang dikirim dari Service
                    String alasanFinal = (alasan != null && !alasan.isBlank()) ? alasan : "Data tidak memenuhi syarat verifikasi.";
                    pesan = "Mohon Maaf Sdr/i *" + p.nama_peserta + "*,\n\n" +
                            "Pendaftaran Mudik Gratis Anda *DITOLAK* dengan alasan:\n" +
                            "👉 *" + alasanFinal + "*\n\n" +
                            "Silakan login kembali untuk memperbaiki data:";
                    linkAction = frontendUrl + "/login";
                    break;

                case "DITERIMA(H-3)":
                    pesan = "⚠️ *KONFIRMASI KEHADIRAN (H-3)* ⚠️\n\n" +
                            "Halo Sdr/i *" + p.nama_peserta + "*,\n" +
                            "Mohon klik link berikut untuk konfirmasi keberangkatan: ";
                    linkAction = frontendUrl + "/konfirmasi/" + p.uuid;
                    break;

                default:
                    pesan = "Halo, ini informasi dari Panitia Mudik Gratis Dishub Aceh.";
                    linkAction = frontendUrl;
                    break;
            }

            String fullMessage = pesan + "\n" + linkAction;
            return "https://wa.me/" + hpFormat + "?text=" + URLEncoder.encode(fullMessage, StandardCharsets.UTF_8.toString());

        } catch (Exception e) {
            System.err.println("Gagal generate link WA: " + e.getMessage());
            return "#";
        }
    }
}