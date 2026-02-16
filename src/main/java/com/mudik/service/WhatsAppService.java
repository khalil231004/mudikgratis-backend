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

    public String generateLink(String noHp, String tipe, PendaftaranMudik p) {
        if (noHp == null || noHp.length() < 7) return "#";

        String hpFormat = noHp.replaceAll("[^0-9]", "");
        if (hpFormat.startsWith("0")) hpFormat = "62" + hpFormat.substring(1);

        // 🔥 LOGIC UUID: Pake UUID, kalau null pake string default
        String uuidSafe = (p.uuid != null && !p.uuid.isEmpty()) ? p.uuid : "ERROR-UUID-NULL";

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
                    pesan = "Mohon Maaf Sdr/i *" + p.nama_peserta + "*,\n\n" +
                            "Pendaftaran Mudik Gratis Anda *TIDAK LOLOS VERIFIKASI*.";
                    linkAction = frontendUrl + "/login";
                    break;

                // 🔥 GANTI JADI TERVERIFIKASI BIAR SINKRON SAMA DB/CURL
                case "DITERIMA(H-3)":
                    pesan = "⚠️ *KONFIRMASI KEHADIRAN (H-3)* ⚠️\n\n" +
                            "Halo Sdr/i *" + p.nama_peserta + "*,\n" +
                            "Mohon klik link untuk konfirmasi keberangkatan: ";
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
            e.printStackTrace();
            return "#";
        }
    }
}