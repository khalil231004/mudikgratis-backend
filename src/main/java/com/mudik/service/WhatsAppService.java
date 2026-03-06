package com.mudik.service;

import com.mudik.model.PendaftaranMudik;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Optional;

@ApplicationScoped
public class WhatsAppService {

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

    // Timeout 10 detik agar tidak hang terlalu lama saat SAPA offline
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    // =========================================================================
    // PUBLIC: KIRIM PESAN (berdasarkan tipe)
    // =========================================================================

    public String sendMessage(String noHp, String tipe, PendaftaranMudik p, String alasan) {
        if (noHp == null || noHp.trim().length() < 7) {
            System.out.println("[WA] Skip: nomor tidak valid");
            return "SKIP: nomor tidak valid";
        }
        String noHpClean = formatNoHp(noHp);
        String pesan = buildPesan(tipe, p, alasan);
        return kirimViaSapa(noHpClean, pesan);
    }

    /**
     * Blast WA berdasarkan tipe aksi admin.
     * Tipe yang didukung: WA_VERIF, WA_H3, WA_TIKET, WA_BATAL
     * Return: "OK" jika berhasil, "SAPA_OFFLINE:..." / "ERROR:..." jika gagal.
     */
    public String sendBlast(String noHp, String tipeAksi, PendaftaranMudik p) {
        if (noHp == null || noHp.trim().length() < 7)
            return "SKIP: nomor tidak valid";

        String noHpClean = formatNoHp(noHp);
        String pesan = buildPesanBlast(tipeAksi, p);
        if (pesan == null)
            return "SKIP: tipe aksi tidak dikenali: " + tipeAksi;

        return kirimViaSapa(noHpClean, pesan);
    }

    public String sendKuotaPenuh(String noHp, String namaPeserta, String namaRute) {
        if (noHp == null || noHp.trim().length() < 7) return "SKIP: nomor tidak valid";
        String noHpClean = formatNoHp(noHp);
        String pesan =
                "👋 *Salam Seulamat dari Dishub Aceh*\n\n" +
                        "Yth. Sdr/i *" + namaPeserta + "*,\n" +
                        "Kami informasikan bahwa *KUOTA RUTE " + namaRute.toUpperCase() + " TELAH PENUH*.\n\n" +
                        "Mohon maaf atas ketidaknyamanannya. Pantau terus dashboard untuk informasi ketersediaan kursi.\n\n" +
                        "📞 *Hotline:* 08217653093\n\n" +
                        "Pesan otomatis Sistem Mudik Gratis Dishub Aceh";
        return kirimViaSapa(noHpClean, pesan);
    }

    // =========================================================================
    // LEGACY WRAPPERS (backward-compat)
    // =========================================================================

    @Deprecated
    public String generateLink(String noHp, String tipe, PendaftaranMudik p, String alasan) {
        sendMessage(noHp, tipe, p, alasan);
        return "";
    }

    @Deprecated
    public String generateKuotaPenuhLink(String noHp, String namaPeserta, String namaRute) {
        sendKuotaPenuh(noHp, namaPeserta, namaRute);
        return "";
    }

    // =========================================================================
    // PRIVATE: BUILDER PESAN
    // =========================================================================

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
            default:
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

    /**
     * Builder pesan untuk aksi WA Blast dari admin panel.
     * Mengembalikan null jika tipe tidak dikenali.
     */
    private String buildPesanBlast(String tipeAksi, PendaftaranMudik p) {
        String baseUrl   = frontendUrlOpt.orElse("https://dishubosrm.acehprov.go.id");
        String hotline   = "📞 *Hotline Mudik Gratis Dishub Aceh:*\n08217653093 (WhatsApp)";
        String footer    = "\n\n" + hotline + "\n\n_Pesan otomatis Sistem Mudik Gratis Dishub Aceh_";
        String nama      = (p != null && p.nama_peserta != null) ? p.nama_peserta : "-";
        String ruteStr   = (p != null && p.rute != null && p.rute.tujuan != null) ? p.rute.tujuan : "-";

        switch (tipeAksi.toUpperCase()) {

            // ── Blast: Sedang Diverifikasi ──────────────────────────────────
            case "WA_VERIF":
                return "👋 *Salam Seulamat dari Dishub Aceh*\n\n" +
                        "Yth. Sdr/i *" + nama + "*,\n" +
                        "Data pendaftaran Mudik Gratis Anda sedang *DALAM PROSES VERIFIKASI* oleh tim petugas.\n\n" +
                        "Mohon bersabar. Anda akan mendapat notifikasi WhatsApp kembali setelah verifikasi selesai.\n\n" +
                        "Pantau status terkini di Dashboard aplikasi Seulamat:\n" + baseUrl + "/dashboard" +
                        footer;

            // ── Blast: Konfirmasi Kehadiran H-3 ────────────────────────────
            case "WA_H3":
                String konfLink = baseUrl + (p != null && p.uuid != null
                        ? "/konfirmasi/" + p.uuid
                        : "/dashboard");
                return "⚠️ *PENTING: KONFIRMASI KEHADIRAN* ⚠️\n\n" +
                        "Halo Sdr/i *" + nama + "*,\n" +
                        "Keberangkatan Mudik Gratis sudah semakin dekat! " +
                        "Kami mohon Anda segera melakukan *KONFIRMASI KEHADIRAN* rombongan.\n\n" +
                        "🔗 Klik link berikut untuk konfirmasi (WAJIB):\n" +
                        konfLink + "\n\n" +
                        "⚠️ *Peserta yang tidak melakukan konfirmasi akan dianggap BATAL.*\n\n" +
                        "Batas waktu konfirmasi sesuai jadwal yang tertera di Dashboard Anda." +
                        footer;

            // ── Blast: Info Tiket / Bus ─────────────────────────────────────
            case "WA_TIKET":
                String namaBus   = (p != null && p.kendaraan != null && p.kendaraan.nama_armada != null)
                        ? p.kendaraan.nama_armada : "akan diinformasikan";
                String platNomor = (p != null && p.kendaraan != null && p.kendaraan.plat_nomor != null)
                        ? p.kendaraan.plat_nomor : "-";
                return "🎫 *INFORMASI TIKET MUDIK GRATIS* 🎫\n\n" +
                        "Yth. Sdr/i *" + nama + "*,\n" +
                        "Selamat! Rombongan Anda telah ditetapkan di armada berikut:\n\n" +
                        "🚌 *Armada:* " + namaBus + "\n" +
                        "🚗 *Plat Nomor:* " + platNomor + "\n" +
                        "📍 *Rute:* Banda Aceh → " + ruteStr + "\n\n" +
                        "Silakan lihat detail tiket dan lokasi keberangkatan di aplikasi Seulamat:\n" +
                        baseUrl + "/dashboard\n\n" +
                        "Harap hadir tepat waktu sesuai jadwal keberangkatan." +
                        footer;

            // ── Blast: Pembatalan ───────────────────────────────────────────
            case "WA_BATAL":
                return "🔔 *PEMBERITAHUAN PEMBATALAN* 🔔\n\n" +
                        "Yth. Sdr/i *" + nama + "*,\n" +
                        "Kami memberitahukan bahwa pendaftaran Mudik Gratis Anda telah *DIBATALKAN* oleh Admin.\n\n" +
                        "Jika Anda merasa keberatan atau ada kesalahan, segera hubungi panitia:\n\n" +
                        hotline + "\n\n" +
                        "Mohon maaf atas ketidaknyamanannya.\n\n" +
                        "_Pesan otomatis Sistem Mudik Gratis Dishub Aceh_";

            default:
                return null;
        }
    }

    // =========================================================================
    // PRIVATE: KIRIM VIA SAPA (dengan error handling terstruktur)
    // =========================================================================

    private String kirimViaSapa(String noHp, String pesan) {
        try {
            String token = getToken();
            return kirimPesan(token, noHp, pesan);
        } catch (Exception e) {
            String msg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            if (msg.startsWith("SAPA_AUTH_ERROR") || msg.contains("Auth gagal") || msg.contains("Token tidak ditemukan")) {
                System.err.println("[WA] SAPA Auth gagal: " + msg);
                return "SAPA_AUTH_ERROR: " + msg;
            }
            // Network/timeout/connection — sinyal ke frontend untuk fallback wa.me
            System.err.println("[WA] SAPA tidak terhubung: " + msg);
            return "SAPA_OFFLINE: " + msg;
        }
    }

    private String getToken() throws Exception {
        String authBody = "{\"email\":\"" + sapaEmail + "\",\"password\":\"" + sapaPassword + "\"}";
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(sapaAuthUrl))
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .timeout(Duration.ofSeconds(10))
                    .POST(HttpRequest.BodyPublishers.ofString(authBody))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200)
                throw new RuntimeException("SAPA_AUTH_ERROR: HTTP " + response.statusCode() + " dari SAPA Auth");

            String body  = response.body();
            String token = extractJsonString(body, "token");
            if (token == null || token.isEmpty()) token = extractJsonString(body, "access_token");
            if (token == null || token.isEmpty())
                throw new RuntimeException("SAPA_AUTH_ERROR: Token tidak ditemukan di response SAPA Auth. Response: " +
                        body.substring(0, Math.min(body.length(), 200)));

            return token;

        } catch (Exception e) {
            throw e; // propagate ke kirimViaSapa
        }
    }

    private String kirimPesan(String token, String noHp, String pesan) throws Exception {
        String pesanEscaped = pesan
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "")
                .replace("\t", "\\t");

        String kirimBody = "{\"no_hp\":\"" + noHp + "\",\"pesan\":\"" + pesanEscaped + "\"}";
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(sapaKirimUrl))
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .header("Authorization", "Bearer " + token)
                    .timeout(Duration.ofSeconds(15))
                    .POST(HttpRequest.BodyPublishers.ofString(kirimBody))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                System.out.println("[WA] OK → " + noHp);
                return "OK";
            } else {
                System.err.println("[WA] Gagal → " + noHp + " | HTTP " + response.statusCode() + " | " + response.body());
                return "ERROR: HTTP " + response.statusCode();
            }
        } catch (Exception e) {
            throw e; // propagate ke kirimViaSapa
        }
    }

    private String extractJsonString(String json, String key) {
        String search = "\"" + key + "\"";
        int keyIdx = json.indexOf(search);
        if (keyIdx < 0) return null;
        int colonIdx = json.indexOf(":", keyIdx + search.length());
        if (colonIdx < 0) return null;
        int valStart = colonIdx + 1;
        while (valStart < json.length() && Character.isWhitespace(json.charAt(valStart))) valStart++;
        if (valStart >= json.length() || json.charAt(valStart) != '"') return null;
        int q2 = json.indexOf("\"", valStart + 1);
        if (q2 < 0) return null;
        return json.substring(valStart + 1, q2);
    }

    private String formatNoHp(String noHp) {
        String clean = noHp.replaceAll("[^0-9]", "");
        if (clean.startsWith("62")) clean = "0" + clean.substring(2);
        if (!clean.startsWith("0")) clean = "0" + clean;
        return clean;
    }

}