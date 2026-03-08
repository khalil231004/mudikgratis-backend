package com.mudik.service;

import com.lowagie.text.*;
import com.lowagie.text.pdf.*;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.mudik.model.PendaftaranMudik;
import jakarta.enterprise.context.ApplicationScoped;

import java.awt.Color;
import java.io.ByteArrayOutputStream;

@ApplicationScoped
public class TiketService {

    // ── Warna brand ────────────────────────────────────────────────────────
    private static final Color DARK_BG       = new Color(0x05, 0x0D, 0x1F);
    private static final Color DARK_BG2      = new Color(0x0D, 0x2A, 0x58);
    private static final Color BLUE_PRIMARY  = new Color(0x25, 0x63, 0xEB);
    private static final Color BLUE_LIGHT    = new Color(0x93, 0xC5, 0xFD);
    private static final Color WHITE         = Color.WHITE;
    private static final Color SLATE_900     = new Color(0x0F, 0x17, 0x2A);
    private static final Color SLATE_500     = new Color(0x64, 0x74, 0x8B);
    private static final Color SLATE_200     = new Color(0xCB, 0xD5, 0xE1);
    private static final Color CARD_BG       = new Color(0xF8, 0xFA, 0xFF);

    public byte[] cetakTiket(PendaftaranMudik pendaftar) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        // A4 portrait
        Rectangle pageSize = PageSize.A4;
        Document document = new Document(pageSize, 0, 0, 0, 0);
        PdfWriter writer = PdfWriter.getInstance(document, out);
        document.open();

        PdfContentByte cb = writer.getDirectContent();
        float W = pageSize.getWidth();   // 595
        float H = pageSize.getHeight();  // 842

        // ── DATA ────────────────────────────────────────────────────────────
        String kode       = pendaftar.kode_booking != null ? pendaftar.kode_booking : "MDK-" + pendaftar.pendaftaran_id;
        String nama       = pendaftar.nama_peserta  != null ? pendaftar.nama_peserta.toUpperCase() : "-";
        String nik        = pendaftar.nik_peserta   != null ? pendaftar.nik_peserta  : "-";
        String alamat     = pendaftar.alamat_rumah  != null ? pendaftar.alamat_rumah : "-";
        String tgl        = pendaftar.rute != null ? pendaftar.rute.getFormattedDate() : "Jadwal Belum Rilis";
        String ruteAsal   = pendaftar.rute != null && pendaftar.rute.asal   != null ? pendaftar.rute.asal   : "-";
        String ruteTujuan = pendaftar.rute != null && pendaftar.rute.tujuan != null ? pendaftar.rute.tujuan : "-";
        String armada     = pendaftar.kendaraan != null && pendaftar.kendaraan.nama_armada != null
                ? pendaftar.kendaraan.nama_armada : "-";

        // ════════════════════════════════════════════════════════════════════
        //  1. BACKGROUND GRADIEN GELAP
        // ════════════════════════════════════════════════════════════════════
        cb.saveState();
        // Atas (lebih gelap)
        cb.setColorFill(DARK_BG);
        cb.rectangle(0, H / 2, W, H / 2);
        cb.fill();
        // Bawah (sedikit lebih terang)
        cb.setColorFill(DARK_BG2);
        cb.rectangle(0, 0, W, H / 2);
        cb.fill();
        cb.restoreState();

        // ════════════════════════════════════════════════════════════════════
        //  2. BLOB GLOW KIRI ATAS
        // ════════════════════════════════════════════════════════════════════
        drawGlow(cb, -40, H - 160, 280, new Color(0x1A, 0x4B, 0x8C));

        // ════════════════════════════════════════════════════════════════════
        //  3. HEADER: Logo kiri & kanan
        // ════════════════════════════════════════════════════════════════════
        float headerY = H - 70;

        // Kiri: DISHUB ACEH
        cb.saveState();
        cb.setColorFill(WHITE);
        cb.beginText();
        cb.setFontAndSize(BaseFont.createFont(BaseFont.HELVETICA_BOLD, BaseFont.WINANSI, false), 11);
        cb.showTextAligned(PdfContentByte.ALIGN_LEFT, "DISHUB ACEH", 40, headerY, 0);
        cb.setFontAndSize(BaseFont.createFont(BaseFont.HELVETICA, BaseFont.WINANSI, false), 9);
        cb.setColorFill(BLUE_LIGHT);
        cb.showTextAligned(PdfContentByte.ALIGN_LEFT, "Pemerintah Aceh", 40, headerY - 14, 0);
        cb.endText();
        cb.restoreState();

        // Kanan: Mudik Gratis
        cb.saveState();
        cb.setColorFill(WHITE);
        cb.beginText();
        cb.setFontAndSize(BaseFont.createFont(BaseFont.HELVETICA_BOLD, BaseFont.WINANSI, false), 11);
        cb.showTextAligned(PdfContentByte.ALIGN_RIGHT, "Mudik Gratis", W - 40, headerY, 0);
        cb.setFontAndSize(BaseFont.createFont(BaseFont.HELVETICA, BaseFont.WINANSI, false), 9);
        cb.setColorFill(BLUE_LIGHT);
        cb.showTextAligned(PdfContentByte.ALIGN_RIGHT, "Pemerintah Aceh", W - 40, headerY - 14, 0);
        cb.endText();
        cb.restoreState();

        // ════════════════════════════════════════════════════════════════════
        //  4. JUDUL E-TIKET
        // ════════════════════════════════════════════════════════════════════
        float titleY = H - 130;
        BaseFont bfBold = BaseFont.createFont(BaseFont.HELVETICA_BOLD, BaseFont.WINANSI, false);
        BaseFont bfPlain = BaseFont.createFont(BaseFont.HELVETICA, BaseFont.WINANSI, false);

        cb.saveState();
        cb.setColorFill(WHITE);
        cb.beginText();
        cb.setFontAndSize(bfBold, 64);
        cb.showTextAligned(PdfContentByte.ALIGN_CENTER, "E-TIKET", W / 2, titleY, 0);
        cb.setFontAndSize(bfBold, 14);
        cb.setColorFill(BLUE_LIGHT);
        cb.showTextAligned(PdfContentByte.ALIGN_CENTER, "MUDIK GRATIS", W / 2, titleY - 26, 0);
        cb.showTextAligned(PdfContentByte.ALIGN_CENTER, "PEMERINTAH ACEH 2026", W / 2, titleY - 44, 0);
        cb.endText();
        cb.restoreState();

        // ════════════════════════════════════════════════════════════════════
        //  5. KARTU PUTIH
        // ════════════════════════════════════════════════════════════════════
        float cardX   = 30;
        float cardY   = 90;           // bottom y
        float cardW   = W - 60;
        float cardH   = H - 240;      // height
        float cardTop = cardY + cardH;
        float r       = 20;

        // Shadow
        cb.saveState();
        cb.setColorFill(new Color(0, 0, 0, 60));
        roundRect(cb, cardX + 4, cardY + 4, cardW, cardH, r);
        cb.fill();
        cb.restoreState();

        // Card body
        cb.saveState();
        cb.setColorFill(WHITE);
        roundRect(cb, cardX, cardY, cardW, cardH, r);
        cb.fill();
        cb.restoreState();

        // ── Kode booking ────────────────────────────────────────────────────
        cb.saveState();
        cb.setColorFill(BLUE_PRIMARY);
        cb.beginText();
        cb.setFontAndSize(bfBold, 14);
        cb.showTextAligned(PdfContentByte.ALIGN_CENTER, kode, W / 2, cardTop - 26, 0);
        cb.endText();
        // Garis separator
        cb.setColorStroke(SLATE_200);
        cb.setLineWidth(0.8f);
        cb.moveTo(cardX + 20, cardTop - 36);
        cb.lineTo(cardX + cardW - 20, cardTop - 36);
        cb.stroke();
        cb.restoreState();

        // ── QR Code (kanan atas card) ────────────────────────────────────────
        String qrData = kode + ";" + nik + ";BUS:" + armada;
        byte[] qrBytes = generateQRBytes(qrData, 180, 180);
        Image qrImg = Image.getInstance(qrBytes);
        qrImg.setAbsolutePosition(cardX + cardW - 160, cardTop - 210);
        qrImg.scaleAbsolute(140, 140);

        // Border biru QR
        cb.saveState();
        cb.setColorFill(BLUE_PRIMARY);
        roundRect(cb, cardX + cardW - 165, cardTop - 215, 150, 150, 8);
        cb.fill();
        cb.restoreState();
        document.add(qrImg);

        // ── Fields data peserta ─────────────────────────────────────────────
        float fieldX = cardX + 22;
        float fy     = cardTop - 66;
        float lineH  = 52;

        drawField(cb, bfBold, bfPlain, fieldX, fy,          "Nama Penumpang", nama,   12);
        drawField(cb, bfBold, bfPlain, fieldX, fy - lineH,  "NIK",            nik,    12);
        drawField(cb, bfBold, bfPlain, fieldX, fy - lineH*2,"Alamat Domisili",alamat, 12);

        // ── Strip keberangkatan ──────────────────────────────────────────────
        float stripY = cardTop - 240;
        cb.saveState();
        cb.setColorFill(BLUE_PRIMARY);
        roundRect(cb, cardX + 16, stripY, cardW - 32, 34, 8);
        cb.fill();
        cb.setColorFill(WHITE);
        cb.beginText();
        cb.setFontAndSize(bfBold, 11);
        cb.showTextAligned(PdfContentByte.ALIGN_CENTER, "Keberangkatan : " + tgl, W / 2, stripY + 10, 0);
        cb.endText();
        cb.restoreState();

        // ── Rute & Armada ────────────────────────────────────────────────────
        float ruteY = stripY - 56;
        drawField(cb, bfBold, bfPlain, fieldX,            ruteY, "Rute Perjalanan", ruteAsal + "-" + ruteTujuan, 12);
        drawField(cb, bfBold, bfPlain, W / 2 + 10,        ruteY, "Armada Bus",      armada,                      12);

        // ── Dashed separator ────────────────────────────────────────────────
        float dashY = stripY - 106;
        cb.saveState();
        cb.setColorStroke(SLATE_200);
        cb.setLineWidth(1f);
        float[] dash = {6f, 4f};
        cb.setLineDash(dash, 0);
        cb.moveTo(cardX + 30, dashY);
        cb.lineTo(cardX + cardW - 30, dashY);
        cb.stroke();
        // Notch kiri
        cb.setColorFill(DARK_BG2);
        cb.circle(cardX, dashY, 12);
        cb.fill();
        // Notch kanan
        cb.circle(cardX + cardW, dashY, 12);
        cb.fill();
        cb.restoreState();

        // ── Catatan bawah ────────────────────────────────────────────────────
        float noteY = dashY - 28;
        cb.saveState();
        cb.setColorFill(SLATE_500);
        cb.beginText();
        cb.setFontAndSize(bfPlain, 10);
        cb.showTextAligned(PdfContentByte.ALIGN_CENTER, "*Harap datang 1 jam sebelum keberangkatan", W / 2, noteY, 0);
        cb.showTextAligned(PdfContentByte.ALIGN_CENTER, "*Tunjukkan QR Code ini kepada petugas", W / 2, noteY - 16, 0);
        cb.endText();

        // Layanan pengaduan
        cb.setColorFill(SLATE_500);
        cb.beginText();
        cb.setFontAndSize(bfPlain, 10);
        cb.showTextAligned(PdfContentByte.ALIGN_CENTER, "Layanan Pengaduan (WA) :", W / 2, noteY - 38, 0);
        cb.endText();
        cb.setColorFill(BLUE_PRIMARY);
        cb.beginText();
        cb.setFontAndSize(bfBold, 11);
        cb.showTextAligned(PdfContentByte.ALIGN_CENTER, "08217653093 / 08217653095", W / 2, noteY - 54, 0);
        cb.endText();
        cb.restoreState();

        // ════════════════════════════════════════════════════════════════════
        //  6. GELOMBANG BAWAH
        // ════════════════════════════════════════════════════════════════════
        drawWaves(cb, W, 80);

        // ── Footer text ──────────────────────────────────────────────────────
        cb.saveState();
        cb.setColorFill(BLUE_LIGHT);
        cb.beginText();
        cb.setFontAndSize(bfPlain, 9);
        cb.showTextAligned(PdfContentByte.ALIGN_CENTER,
                "Dinas Perhubungan Aceh  \u2022  Pemerintah Aceh 2026", W / 2, 20, 0);
        cb.endText();
        cb.restoreState();

        document.close();
        return out.toByteArray();
    }

    // ════════════════════════════════════════════════════════════════════════
    //  HELPERS
    // ════════════════════════════════════════════════════════════════════════

    private void drawField(PdfContentByte cb, BaseFont bfBold, BaseFont bfPlain,
                           float x, float y, String label, String value, float fontSize) throws Exception {
        cb.saveState();
        cb.setColorFill(BLUE_PRIMARY);
        cb.beginText();
        cb.setFontAndSize(bfPlain, 9);
        cb.showTextAligned(PdfContentByte.ALIGN_LEFT, label, x, y, 0);
        cb.endText();
        cb.setColorFill(SLATE_900);
        cb.beginText();
        cb.setFontAndSize(bfBold, fontSize);
        cb.showTextAligned(PdfContentByte.ALIGN_LEFT, value != null ? value : "-", x, y - 16, 0);
        cb.endText();
        cb.restoreState();
    }

    private void roundRect(PdfContentByte cb, float x, float y, float w, float h, float r) {
        cb.roundRectangle(x, y, w, h, r);
    }

    private void drawGlow(PdfContentByte cb, float cx, float cy, float size, Color color) {
        // Simulasi glow dengan lingkaran transparan berlapis
        for (int i = 5; i >= 1; i--) {
            float alpha = 0.04f * i;
            float s = size * (1f + (5 - i) * 0.15f);
            cb.saveState();
            cb.setColorFill(new Color(
                    color.getRed(), color.getGreen(), color.getBlue(),
                    Math.min(255, (int)(alpha * 255))
            ));
            cb.circle(cx + size / 2, cy + size / 2, s / 2);
            cb.fill();
            cb.restoreState();
        }
    }

    private void drawWaves(PdfContentByte cb, float W, float baseH) {
        cb.saveState();
        // Wave 1
        cb.setColorFill(new Color(0x1A, 0x4B, 0x8C, 120));
        cb.moveTo(0, baseH);
        for (float x = 0; x <= W; x += 80) {
            cb.curveTo(x + 20, baseH + 28, x + 60, baseH - 10, x + 80, baseH);
        }
        cb.lineTo(W, 0);
        cb.lineTo(0, 0);
        cb.closePath();
        cb.fill();
        // Wave 2
        cb.setColorFill(new Color(0x25, 0x63, 0xEB, 140));
        cb.moveTo(0, baseH - 20);
        for (float x = 0; x <= W; x += 100) {
            cb.curveTo(x + 25, baseH + 14, x + 75, baseH - 40, x + 100, baseH - 20);
        }
        cb.lineTo(W, 0);
        cb.lineTo(0, 0);
        cb.closePath();
        cb.fill();
        cb.restoreState();
    }

    private byte[] generateQRBytes(String text, int w, int h) throws Exception {
        QRCodeWriter writer = new QRCodeWriter();
        BitMatrix matrix = writer.encode(text, BarcodeFormat.QR_CODE, w, h);
        ByteArrayOutputStream png = new ByteArrayOutputStream();
        MatrixToImageWriter.writeToStream(matrix, "PNG", png);
        return png.toByteArray();
    }
}