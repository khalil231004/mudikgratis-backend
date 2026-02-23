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
        // FIX 9: Tambahkan Hotline nomor
        String hotline = "📞 *Hubungi Admin:*\nNana (Verifikasi): 0821-7653-095\nMega (Info Umum): 0821-7653-093";
        String footer = "\n\n" + hotline + "\n\n_Pesan otomatis Sistem Mudik Gratis Dishub Aceh_";

        try {
            switch (tipe) {
                case "TOLAK_DATA":
                    String alasanFinal = (alasan != null && !alasan.isBlank()) ? alasan : "Data belum lengkap.";
                    pesan = "👋 *Salam Seulamat dari Dishub Aceh*\n\n" +
                            "Yth. Sdr/i *" + p.nama_peserta + "*,\n" +
                            "Mohon maaf, Verifikasi Pendaftaran Mudik Anda *BELUM LENGKAP*.\n\n" +
                            "Catatan Petugas:\n" +
                            "👉 *" + alasanFinal + "*\n\n" +
                            "ℹ️ *Perbaikan dapat dilakukan selama kuota masih tersedia.*\n\n" +
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

    // ── NOTIFIKASI KE ADMIN (saat user daftar baru / perbaiki data) ──
    // Admin Nana (status/penolakan): 08217653095
    // Admin Mega (info umum): 08217653093
    public String generateAdminLink(String tipe, String namaPeserta, String noHpUser, int jumlahPeserta) {
        // Notifikasi ke Admin Nana (verifikasi & penolakan)
        String adminNana = "628217653095";
        String baseUrl = frontendUrlOpt.orElse("https://dishubosrm.acehprov.go.id");
        String pesan = "";

        try {
            switch (tipe) {
                case "PENDAFTAR_BARU":
                    pesan = "🔔 *Notifikasi Pendaftar Baru*\n\n" +
                            "Nama: *" + namaPeserta + "*\n" +
                            "Jumlah peserta: *" + jumlahPeserta + " orang*\n" +
                            "No HP user: " + (noHpUser != null && !noHpUser.isBlank() ? noHpUser : "-") + "\n\n" +
                            "Silakan verifikasi di dashboard admin:\n" + baseUrl + "/admin";
                    break;
                case "DATA_DIPERBAIKI":
                    pesan = "🔄 *Data Diperbaiki - Perlu Re-Verifikasi*\n\n" +
                            "Peserta *" + namaPeserta + "* telah memperbaiki data yang ditolak.\n" +
                            "No HP user: " + (noHpUser != null && !noHpUser.isBlank() ? noHpUser : "-") + "\n\n" +
                            "Silakan verifikasi ulang di dashboard admin:\n" + baseUrl + "/admin";
                    break;
                default:
                    pesan = "🔔 Ada aktivitas baru dari peserta *" + namaPeserta + "*. Cek dashboard admin: " + baseUrl + "/admin";
            }

            String encodedPesan = java.net.URLEncoder.encode(pesan, java.nio.charset.StandardCharsets.UTF_8.toString());
            return "https://wa.me/" + adminNana + "?text=" + encodedPesan;
        } catch (Exception e) {
            System.err.println("ERROR generateAdminLink: " + e.getMessage());
            return "#";
        }
    }

    // FIX 10: Notifikasi kuota penuh ke penumpang
    public String generateKuotaPenuhLink(String noHp, String namaPeserta, String namaRute) {
        if (noHp == null || noHp.length() < 7) return "#";
        String hpFormat = noHp.replaceAll("[^0-9]", "");
        if (hpFormat.startsWith("0")) hpFormat = "62" + hpFormat.substring(1);

        String baseUrl = frontendUrlOpt.orElse("https://dishubosrm.acehprov.go.id");
        String hotline = "📞 *Admin:* 0821-7653-095 / 0821-7653-093";
        try {
            String pesan = "👋 *Salam Seulamat dari Dishub Aceh*\n\n" +
                    "Yth. Sdr/i *" + namaPeserta + "*,\n" +
                    "Kami informasikan bahwa *KUOTA RUTE " + namaRute.toUpperCase() + " TELAH PENUH*.\n\n" +
                    "Mohon maaf atas ketidaknyamanannya. Pantau terus dashboard untuk informasi ketersediaan kursi.\n\n" +
                    hotline + "\n\n" +
                    "_Pesan otomatis Sistem Mudik Gratis Dishub Aceh_";
            return "https://wa.me/" + hpFormat + "?text=" + URLEncoder.encode(pesan, StandardCharsets.UTF_8.toString());
        } catch (Exception e) {
            return "#";
        }
    }}