package com.mudik.resource;

import io.quarkus.mailer.Mail;
import io.quarkus.mailer.Mailer;
import io.quarkus.mailer.MailerName;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import io.vertx.ext.mail.MailConfig;
import io.vertx.ext.mail.StartTLSOptions;

@Path("/test")
public class TestEmailResource {

    @Inject
    Mailer mailer;

    @GET
    @Produces(MediaType.TEXT_PLAIN)
    public String kirimTestManual() {
        // --- DATA RAHASIA (HARDCODE DISINI) ---
        // Masukkan Email & Password App 16 Digit kamu disini
        // HATI-HATI: Pastikan tidak ada spasi!
        String MY_EMAIL = "khalilgaming64@gmail.com";
        String MY_PASSWORD = "ootbzfxxslqqdwlz";
        // ---------------------------------------

        // Kita kirim email simpel
        try {
            // Catatan: Ini pakai config dari properties dulu,
            // tapi kita pastikan isi pesannya jelas.
            // Kalau ini masih gagal 535, berarti MEMANG credentialnya ditolak Google.

            mailer.send(Mail.withText(MY_EMAIL, "Tes Hardcore", "Kalau ini masuk, berarti password benar!"));
            return "✅ PERINTAH KIRIM SUKSES! Cek Inbox.";
        } catch (Exception e) {
            return "❌ GAGAL: " + e.getMessage();
        }
    }
}