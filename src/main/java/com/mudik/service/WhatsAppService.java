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
 *  2. kirimPesan() → POST /service_name/WhatsApp/KirimPesan + Bearer token
 */
@ApplicationScoped
public class WhatsAppService {

    // ── Config — bisa di-override via application.properties / ENV ──────────
    @ConfigProperty(name = "app.frontend.url")
    Optional<String> frontendUrlOpt;

    @ConfigProperty(name = "sapa.api.auth.url", defaultValue = "https://sapa.acehprov.go.id/Api/Auth")
    String sapaAuthUrl;

    // ⚠️ FIX: URL KirimPesan harus pakai service_name sesuai akun SAPA
    // Format: Base_url/service_name/WhatsApp/KirimPesan
    // Ganti "MudikDishub" dengan service_name akun SAPA yang sebenarnya
    @ConfigProperty(name = "sapa.api.kirim.url", defaultValue = "https://sapa.acehprov.go.id/MudikDishub/WhatsApp/KirimPesan")
    String sapaKirimUrl;

    @ConfigProperty(name = "sapa.api.email", defaultValue = "selamataceh@dishub.com")
    String sapaEmail;

    @ConfigProperty(name = "sapa.api.password", defaultValue = "selamatMudik")
    String sapaPassword;

    private final HttpClient httpClient = HttpClient.newHttpClient();

    // ── FIX #1: DEBUG = true agar selalu ada log di server untuk diagnosa ──
    private static final boolean DEBUG = true;

    // =========================================================================
    // PUBLIC: sendMessage — pengganti generateLink()
    // =========================================================================

    public String sendMessage(String noHp, String tipe, PendaftaranMudik p, String alasan) {
        System.out.println("========== SAPA WA BLAST ==========");
        System.out.println("[WA] No HP Input : " + noHp);
        System.out.println("[WA] Tipe        : " + tipe);
        System.out.println("[WA] Nama        : " + (p != null ? p.nama_peserta : "null"));
        System.out.println("[WA] Auth URL    : " + sapaAuthUrl);
        System.out.println("[WA] Kirim URL   : " + sapaKirimUrl);
        System.out.println("[WA] Email used  : " + sapaEmail);

        if (noHp == null || noHp.trim().length() < 7) {
            System.out.println("[WA] Skip: nomor tidak valid (" + noHp + ")");
            return "SKIP: nomor tidak valid";
        }

        String noHpClean = formatNoHp(noHp);
        System.out.println("[WA] No HP Clean : " + noHpClean);

        String pesan = buildPesan(tipe, p, alasan);

        try {
            String token = getToken();
            return kirimPesan(token, noHpClean, pesan);
        } catch (Exception e) {
            System.err.println("[WA] ERROR sendMessage: " + e.getMessage());
            e.printStackTrace();
            return "ERROR: " + e.getMessage();
        }
    }

    public String sendKuotaPenuh(String noHp, String namaPeserta, String namaRute) {
        if (noHp == null || noHp.trim().length() < 7) return "SKIP: nomor tidak valid";

        String noHpClean = formatNoHp(noHp);
        String hotline = "📞 *Hotline:* 08217653093";
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
            e.printStackTrace();
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
        // ⚠️ FIX #4: SAPA terima format 08xx (bukan 628xx)
        // Normalisasi ke format 08xx
        if (clean.startsWith("62")) {
            clean = "0" + clean.substring(2); // 6281x → 081x
        }
        // Jika sudah 08xx, biarkan
        // Jika mulai 8xx tanpa 0, tambah 0
        if (!clean.startsWith("0")) {
            clean = "0" + clean;
        }
        return clean;
    }

    private String buildPesan(String tipe, PendaftaranMudik p, String alasan) {
        String baseUrl = frontendUrlOpt.orElse("https://dishubosrm.acehprov.go.id");
        String hotline = "📞 *Hotline Mudik Gratis Dishub Aceh:*\n08217653093 (WhatsApp)";
        String footer  = "\n\n" + hotline + "\n\n_Pesan otomatis Sistem Mudik Gratis Dishub Aceh_";
        String nama    = (p != null) ? p.nama_peserta : "-";

        switch (tipe) {
            case "TOLAK_DATA":
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

        System.out.println("[SAPA Auth] Requesting token dari: " + sapaAuthUrl);
        System.out.println("[SAPA Auth] Body: " + authBody);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(sapaAuthUrl))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(authBody))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        System.out.println("[SAPA Auth] Status: " + response.statusCode());
        System.out.println("[SAPA Auth] Body  : " + response.body());

        if (response.statusCode() != 200) {
            throw new RuntimeException("SAPA Auth gagal: HTTP " + response.statusCode() + " | " + response.body());
        }

        // ⚠️ FIX #3: Parse token lebih robust
        // Response bisa: {"status":"success","token":"eyJ..."}
        // atau:          {"status":true,"data":{"token":"eyJ..."}}
        String body = response.body();

        // Coba parse "token" langsung di root
        String token = extractJsonString(body, "token");

        // Fallback: coba "access_token"
        if (token == null || token.isEmpty()) {
            token = extractJsonString(body, "access_token");
        }

        if (token == null || token.isEmpty()) {
            throw new RuntimeException("Token tidak ditemukan di response SAPA Auth. Response: " + body);
        }

        System.out.println("[SAPA Auth] Token OK (length=" + token.length() + ")");
        return token;
    }

    private String kirimPesan(String token, String noHp, String pesan) throws Exception {
        // FIX: Escape JSON dengan benar
        String pesanEscaped = pesan
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "")
                .replace("\t", "\\t");

        String kirimBody = "{\"no_hp\":\"" + noHp + "\",\"pesan\":\"" + pesanEscaped + "\"}";

        System.out.println("[SAPA Kirim] URL  : " + sapaKirimUrl);
        System.out.println("[SAPA Kirim] NoHP : " + noHp);
        System.out.println("[SAPA Kirim] Body : " + kirimBody.substring(0, Math.min(kirimBody.length(), 200)) + "...");

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(sapaKirimUrl))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .header("Authorization", "Bearer " + token)
                .POST(HttpRequest.BodyPublishers.ofString(kirimBody))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        System.out.println("[SAPA Kirim] Status: " + response.statusCode());
        System.out.println("[SAPA Kirim] Body  : " + response.body());

        if (response.statusCode() == 200) {
            System.out.println("[WA] ✅ Terkirim → " + noHp);
            return "OK";
        } else {
            System.err.println("[WA] ❌ Gagal → " + noHp + " | HTTP " + response.statusCode());
            return "ERROR: HTTP " + response.statusCode() + " - " + response.body();
        }
    }

    /**
     * Helper: ekstrak nilai string dari JSON key tertentu.
     * Menangani format: "key":"value" di mana pun posisinya.
     */
    private String extractJsonString(String json, String key) {
        String search = "\"" + key + "\"";
        int keyIdx = json.indexOf(search);
        if (keyIdx < 0) return null;

        int colonIdx = json.indexOf(":", keyIdx + search.length());
        if (colonIdx < 0) return null;

        // Skip whitespace setelah ':'
        int valStart = colonIdx + 1;
        while (valStart < json.length() && Character.isWhitespace(json.charAt(valStart))) valStart++;

        if (valStart >= json.length()) return null;

        if (json.charAt(valStart) == '"') {
            // String value
            int q1 = valStart;
            int q2 = json.indexOf("\"", q1 + 1);
            if (q2 < 0) return null;
            return json.substring(q1 + 1, q2);
        }

        return null;
    }
}