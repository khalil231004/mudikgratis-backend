package com.mudik.service;

import com.mudik.model.PendaftaranMudik;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Optional;

/**
 * WhatsAppService — Mengirim pesan WA via SAPA API Blast (sapa.acehprov.go.id)
 *
 * Alur:
 *  1. getToken()   → POST /Api/Auth → dapat JWT
 *  2. kirimPesan() → POST /Api/WhatsApp/KirimPesan + Bearer token
 *
 * Method lama generateLink() / generateKuotaPenuhLink() tetap ada sebagai
 * wrapper agar tidak ada compile error di PendaftaranService & AdminResource.
 */
@ApplicationScoped
public class WhatsAppService {

    // ── Config — bisa di-override via application.properties / ENV ──────────
    @ConfigProperty(name = "app.frontend.url")
    Optional<String> frontendUrlOpt;

    @ConfigProperty(name = "sapa.api.auth.url", defaultValue = "https://sapa.acehprov.go.id/Api/Auth")
    String sapaAuthUrl;

    @ConfigProperty(name = "sapa.api.kirim.url", defaultValue = "https://sapa.acehprov.go.id/Api/WhatsApp/KirimPesan")
    String sapaKirimUrl;

    @ConfigProperty(name = "sapa.api.email", defaultValue = "selamataceh@dishub.com")
    String sapaEmail;

    @ConfigProperty(name = "sapa.api.password", defaultValue = "selamatMudik")
    String sapaPassword;

    private final HttpClient httpClient = HttpClient.newHttpClient();

    // ── DEBUG MODE: false di production, set true kalau mau trace log ────────
    private static final boolean DEBUG = false;

    // =========================================================================
    // PUBLIC: sendMessage — pengganti generateLink()
    // =========================================================================

    public String sendMessage(String noHp, String tipe, PendaftaranMudik p, String alasan) {
        if (DEBUG) {
            System.out.println("========== SAPA WA BLAST ==========");
            System.out.println("No HP: " + noHp);
            System.out.println("Tipe : " + tipe);
            System.out.println("Nama : " + (p != null ? p.nama_peserta : "null"));
        }

        if (noHp == null || noHp.length() < 7) {
            System.out.println("[WA] Skip: nomor tidak valid (" + noHp + ")");
            return "SKIP: nomor tidak valid";
        }

        String noHpClean = formatNoHp(noHp);
        String pesan     = buildPesan(tipe, p, alasan);

        try {
            String token = getToken();
            return kirimPesan(token, noHpClean, pesan);
        } catch (Exception e) {
            System.err.println("[WA] ERROR sendMessage: " + e.getMessage());
            return "ERROR: " + e.getMessage();
        }
    }

    public String sendKuotaPenuh(String noHp, String namaPeserta, String namaRute) {
        if (noHp == null || noHp.length() < 7) return "SKIP: nomor tidak valid";

        String noHpClean = formatNoHp(noHp);
        String hotline   = "📞 *Hotline:* 08217653093";
        String pesan =
                "👋 *Salam Seulamat dari Dishub Aceh*\n\n" +
                        "Yth. Sdr/i *" + namaPeserta + "*,\n" +
                        "Kami informasikan bahwa *KUOTA RUTE " + namaRute.toUpperCase() + " TELAH PENUH*.\n\n" +
                        "Mohon maaf atas ketidaknyamanannya. Pantau terus dashboard untuk informasi ketersediaan kursi.\n\n" +
                        hotline + "\n\n" +
                        "Pesan otomatis Sistem Mudik Gratis Dishub Aceh";

        try {
            String token = getToken();
            return kirimPesan(token, noHpClean, pesan);
        } catch (Exception e) {
            System.err.println("[WA] ERROR sendKuotaPenuh: " + e.getMessage());
            return "ERROR: " + e.getMessage();
        }
    }

    // =========================================================================
    // LEGACY WRAPPERS — agar tidak ada compile error di kode lama
    // =========================================================================

    @Deprecated
    public String generateLink(String noHp, String tipe, PendaftaranMudik p, String alasan) {
        sendMessage(noHp, tipe, p, alasan);
        return ""; // pesan sudah dikirim via API, tidak perlu return link
    }

    @Deprecated
    public String generateKuotaPenuhLink(String noHp, String namaPeserta, String namaRute) {
        sendKuotaPenuh(noHp, namaPeserta, namaRute);
        return "";
    }

    // =========================================================================
    // PRIVATE HELPERS
    // =========================================================================

    private String formatNoHp(String noHp) {
        String clean = noHp.replaceAll("[^0-9]", "");
        if (clean.startsWith("0")) clean = "62" + clean.substring(1);
        return clean;
    }

    private String buildPesan(String tipe, PendaftaranMudik p, String alasan) {
        String baseUrl = frontendUrlOpt.orElse("https://dishubosrm.acehprov.go.id");
        String hotline = "📞 *Hotline Mudik Gratis Dishub Aceh:*\n08217653093 (WhatsApp)";
        String footer  = "\n\n" + hotline + "\n\n_Pesan otomatis Sistem Mudik Gratis Dishub Aceh_";
        String nama    = (p != null) ? p.nama_peserta : "-";

        switch (tipe) {
            case "TOLAK_DATA":
                // Batas perbaikan 1 jam DIHAPUS atas permintaan klien
                String alasanFinal = (alasan != null && !alasan.isBlank()) ? alasan : "Data belum lengkap.";
                return "👋 *Salam Seulamat dari Dishub Aceh*\n\n" +
                        "Yth. Sdr/i *" + nama + "*,\n" +
                        "Mohon maaf, Verifikasi Pendaftaran Mudik Anda *BELUM LENGKAP*.\n\n" +
                        "Catatan Petugas:\n" +
                        "👉 *" + alasanFinal + "*\n\n" +
                        "Silakan login dan perbaiki data Anda di:\n" + baseUrl +
                        footer;

            case "DITERIMA(H-3)":
                return "👋 *Salam Seulamat dari Dishub Aceh*\n\n" +
                        "Yth. Sdr/i *" + nama + "*,\n" +
                        "Selamat! Pendaftaran Mudik Anda telah *DITERIMA*.\n\n" +
                        "🚍 *PENTING: KONFIRMASI KEBERANGKATAN*\n" +
                        "Agar kursi Anda tidak hangus, WAJIB melakukan konfirmasi kehadiran " +
                        "yang akan *dikirimkan admin* sesuai jadwal. " +
                        "Nanti Anda bisa konfirmasi langsung di *Dashboard* aplikasi Seulamat maupun via link WA yang akan kami kirimkan.\n\n" +
                        "Pantau terus status tiket Anda di Dashboard aplikasi Seulamat." +
                        footer;

            default: // TERIMA
                return "👋 *Salam Seulamat dari Dishub Aceh*\n\n" +
                        "Yth. Sdr/i *" + nama + "*,\n" +
                        "Selamat! Pendaftaran Mudik Anda telah *DITERIMA*.\n\n" +
                        "🚍 *PENTING: KONFIRMASI KEBERANGKATAN*\n" +
                        "Agar kursi Anda tidak hangus, WAJIB melakukan konfirmasi kehadiran " +
                        "yang akan *dikirimkan admin* sesuai jadwal. " +
                        "Nanti Anda bisa konfirmasi langsung di *Dashboard* aplikasi Seulamat maupun via link WA yang akan kami kirimkan.\n\n" +
                        "Pantau terus status tiket Anda di Dashboard aplikasi Seulamat." +
                        footer;
        }
    }

    private String getToken() throws Exception {
        String authBody = "{\"email\":\"" + sapaEmail + "\",\"password\":\"" + sapaPassword + "\"}";

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(sapaAuthUrl))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(authBody))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (DEBUG) {
            System.out.println("[SAPA Auth] Status: " + response.statusCode());
            System.out.println("[SAPA Auth] Body  : " + response.body());
        }

        if (response.statusCode() != 200) {
            throw new RuntimeException("SAPA Auth gagal: HTTP " + response.statusCode());
        }

        // Parse token dari JSON: {"status":"success","token":"eyJ..."}
        String body = response.body();
        int tokenStart = body.indexOf("\"token\":");
        if (tokenStart < 0) throw new RuntimeException("Token tidak ditemukan di response SAPA Auth");

        int q1 = body.indexOf("\"", tokenStart + 8);
        int q2 = body.indexOf("\"", q1 + 1);
        String token = body.substring(q1 + 1, q2);

        if (DEBUG) System.out.println("[SAPA Auth] Token OK (length=" + token.length() + ")");
        return token;
    }

    private String kirimPesan(String token, String noHp, String pesan) throws Exception {
        String pesanEscaped = pesan
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "");

        String kirimBody = "{\"no_hp\":\"" + noHp + "\",\"pesan\":\"" + pesanEscaped + "\"}";

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(sapaKirimUrl))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .header("Authorization", "Bearer " + token)
                .POST(HttpRequest.BodyPublishers.ofString(kirimBody))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (DEBUG) {
            System.out.println("[SAPA Kirim] Status: " + response.statusCode());
            System.out.println("[SAPA Kirim] Body  : " + response.body());
        }

        if (response.statusCode() == 200) {
            System.out.println("[WA] Terkirim → " + noHp);
            return "OK";
        } else {
            System.err.println("[WA] Gagal → " + noHp + " | HTTP " + response.statusCode());
            return "ERROR: HTTP " + response.statusCode() + " - " + response.body();
        }
    }
}