package com.mudik.resource;

import io.quarkus.mailer.Mail;
import io.quarkus.mailer.Mailer;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;


@Path("/test")
public class TestEmailResource {

    @Inject
    Mailer mailer;

    @GET
    @Produces(MediaType.TEXT_PLAIN)
    public String kirimTestManual() {
        String MY_EMAIL = "khalilgaming64@gmail.com";
        String MY_PASSWORD = "ootbzfxxslqqdwlz";
        try {
            mailer.send(Mail.withText(MY_EMAIL, "Tes Hardcore", "Kalau ini masuk, berarti password benar!"));
            return "✅ PERINTAH KIRIM SUKSES! Cek Inbox.";
        } catch (Exception e) {
            return "❌ GAGAL: " + e.getMessage();
        }
    }
}