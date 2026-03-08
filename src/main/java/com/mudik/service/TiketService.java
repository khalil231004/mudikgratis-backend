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
import java.io.InputStream;

@ApplicationScoped
public class TiketService {

    // ── Warna ──────────────────────────────────────────────────────────────
    private static final Color BLUE_PRIMARY    = new Color(0x25, 0x63, 0xEB);
    private static final Color BLUE_LABEL      = new Color(0x29, 0x78, 0xFF);
    private static final Color BLUE_LIGHT_TEXT = new Color(0x93, 0xC5, 0xFD);
    private static final Color WHITE           = Color.WHITE;
    private static final Color SLATE_900       = new Color(0x0F, 0x17, 0x2A);
    private static final Color SLATE_500       = new Color(0x64, 0x74, 0x8B);
    private static final Color SLATE_200       = new Color(0xE2, 0xE8, 0xF0);
    private static final Color BG_DARK         = new Color(0x05, 0x0D, 0x1F);

    public byte[] cetakTiket(PendaftaranMudik pendaftar) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        Document document = new Document(PageSize.A4, 36, 36, 36, 36);
        PdfWriter.getInstance(document, out);
        document.open();

        // ── DATA ────────────────────────────────────────────────────────────
        String kode   = pendaftar.kode_booking != null ? pendaftar.kode_booking : "MDK-" + pendaftar.pendaftaran_id;
        String nama   = pendaftar.nama_peserta  != null ? pendaftar.nama_peserta.toUpperCase() : "-";
        String nik    = pendaftar.nik_peserta   != null ? pendaftar.nik_peserta  : "-";
        String alamat = pendaftar.alamat_rumah  != null ? pendaftar.alamat_rumah : "-";
        String tgl    = pendaftar.rute != null ? pendaftar.rute.getFormattedDate() : "Jadwal Belum Rilis";
        String rute   = pendaftar.rute != null
                ? nvl(pendaftar.rute.asal) + " - " + nvl(pendaftar.rute.tujuan) : "-";
        String armada = (pendaftar.kendaraan != null && pendaftar.kendaraan.nama_armada != null)
                ? pendaftar.kendaraan.nama_armada : "-";

        // ── FONTS ────────────────────────────────────────────────────────────
        BaseFont bf     = BaseFont.createFont(BaseFont.HELVETICA,      BaseFont.WINANSI, false);
        BaseFont bfBold = BaseFont.createFont(BaseFont.HELVETICA_BOLD, BaseFont.WINANSI, false);

        // ── LOAD LOGO ────────────────────────────────────────────────────────
        Image logoDishub   = loadLogo("/logo-pancacita_dishub.png");
        Image logoSeulamat = loadLogo("/Seulamat_Logo-04.png");

        // ════════════════════════════════════════════════════════════════════
        //  1. HEADER: Logo kiri (Dishub) + Logo kanan (Seulamat)
        // ════════════════════════════════════════════════════════════════════
        PdfPTable tHeader = new PdfPTable(3);
        tHeader.setWidthPercentage(100);
        tHeader.setWidths(new float[]{ 3.5f, 2f, 3.5f });
        tHeader.setSpacingAfter(0f);

        // Kiri: logo Dishub Aceh
        PdfPCell cLeft = new PdfPCell();
        cLeft.setBorder(Rectangle.NO_BORDER);
        cLeft.setBackgroundColor(BG_DARK);
        cLeft.setPaddingTop(10);
        cLeft.setPaddingBottom(10);
        cLeft.setPaddingLeft(4);
        cLeft.setVerticalAlignment(Element.ALIGN_MIDDLE);
        if (logoDishub != null) {
            // logo dishub landscape 1502x430 → scale to height ~36pt
            logoDishub.scaleToFit(160, 46);
            cLeft.addElement(logoDishub);
        } else {
            cLeft.addElement(new Phrase("DISHUB ACEH", new Font(bfBold, 10, Font.NORMAL, WHITE)));
        }
        tHeader.addCell(cLeft);

        // Tengah: kosong spacer
        PdfPCell cMid = new PdfPCell(new Phrase(" "));
        cMid.setBorder(Rectangle.NO_BORDER);
        cMid.setBackgroundColor(BG_DARK);
        tHeader.addCell(cMid);

        // Kanan: logo Seulamat
        PdfPCell cRight = new PdfPCell();
        cRight.setBorder(Rectangle.NO_BORDER);
        cRight.setBackgroundColor(BG_DARK);
        cRight.setPaddingTop(10);
        cRight.setPaddingBottom(10);
        cRight.setPaddingRight(4);
        cRight.setHorizontalAlignment(Element.ALIGN_RIGHT);
        cRight.setVerticalAlignment(Element.ALIGN_MIDDLE);
        if (logoSeulamat != null) {
            // logo seulamat landscape 2000x823 → scale to height ~36pt
            logoSeulamat.scaleToFit(120, 46);
            logoSeulamat.setAlignment(Image.ALIGN_RIGHT);
            cRight.addElement(logoSeulamat);
        } else {
            Paragraph pRight = new Paragraph("Seulamat", new Font(bfBold, 10, Font.NORMAL, WHITE));
            pRight.setAlignment(Element.ALIGN_RIGHT);
            cRight.addElement(pRight);
        }
        tHeader.addCell(cRight);

        document.add(tHeader);

        // ════════════════════════════════════════════════════════════════════
        //  2. JUDUL E-TIKET
        // ════════════════════════════════════════════════════════════════════
        PdfPTable tJudul = new PdfPTable(1);
        tJudul.setWidthPercentage(100);
        tJudul.setSpacingAfter(8f);

        Paragraph pJudul = new Paragraph();
        pJudul.setAlignment(Element.ALIGN_CENTER);
        pJudul.add(new Chunk("E-TIKET\n",            new Font(bfBold, 56, Font.NORMAL, WHITE)));
        pJudul.add(new Chunk("MUDIK GRATIS\n",        new Font(bfBold, 13, Font.NORMAL, BLUE_LIGHT_TEXT)));
        pJudul.add(new Chunk("PEMERINTAH ACEH 2026", new Font(bfBold, 13, Font.NORMAL, BLUE_LIGHT_TEXT)));

        PdfPCell cJudul = new PdfPCell();
        cJudul.setBorder(Rectangle.NO_BORDER);
        cJudul.setBackgroundColor(BG_DARK);
        cJudul.setHorizontalAlignment(Element.ALIGN_CENTER);
        cJudul.setPaddingTop(4);
        cJudul.setPaddingBottom(14);
        cJudul.addElement(pJudul);
        tJudul.addCell(cJudul);
        document.add(tJudul);

        // ════════════════════════════════════════════════════════════════════
        //  3. KARTU PUTIH
        // ════════════════════════════════════════════════════════════════════
        PdfPTable tCard = new PdfPTable(1);
        tCard.setWidthPercentage(100);
        tCard.setSpacingAfter(6f);

        PdfPCell cCard = new PdfPCell();
        cCard.setBorder(Rectangle.BOX);
        cCard.setBorderColor(SLATE_200);
        cCard.setBorderWidth(1f);
        cCard.setBackgroundColor(WHITE);
        cCard.setPadding(0);

        // ── Kode booking ──────────────────────────────────────────────────
        PdfPTable tKode = new PdfPTable(1);
        tKode.setWidthPercentage(100);
        PdfPCell cKode = new PdfPCell(new Phrase(kode, new Font(bfBold, 13, Font.NORMAL, BLUE_PRIMARY)));
        cKode.setHorizontalAlignment(Element.ALIGN_CENTER);
        cKode.setBorder(Rectangle.BOTTOM);
        cKode.setBorderColor(SLATE_200);
        cKode.setBorderWidth(0.8f);
        cKode.setBackgroundColor(WHITE);
        cKode.setPaddingTop(12);
        cKode.setPaddingBottom(10);
        tKode.addCell(cKode);
        cCard.addElement(tKode);

        // ── Data peserta (kiri) + QR (kanan) ──────────────────────────────
        PdfPTable tPeserta = new PdfPTable(2);
        tPeserta.setWidthPercentage(100);
        tPeserta.setWidths(new float[]{ 3f, 2f });

        PdfPCell cKiri = new PdfPCell();
        cKiri.setBorder(Rectangle.NO_BORDER);
        cKiri.setBackgroundColor(WHITE);
        cKiri.setPaddingLeft(16);
        cKiri.setPaddingTop(14);
        cKiri.setPaddingRight(8);
        cKiri.setPaddingBottom(10);
        cKiri.addElement(makeField(bf, bfBold, "Nama Penumpang", nama));
        cKiri.addElement(makeSpacer(10));
        cKiri.addElement(makeField(bf, bfBold, "NIK", nik));
        cKiri.addElement(makeSpacer(10));
        cKiri.addElement(makeField(bf, bfBold, "Alamat Domisili", alamat));
        tPeserta.addCell(cKiri);

        // QR code
        String qrData = kode + ";" + nik + ";BUS:" + armada;
        Image qrImg = generateQRCodeImage(qrData, 200);

        PdfPCell cQrOuter = new PdfPCell();
        cQrOuter.setBorder(Rectangle.NO_BORDER);
        cQrOuter.setBackgroundColor(WHITE);
        cQrOuter.setHorizontalAlignment(Element.ALIGN_CENTER);
        cQrOuter.setVerticalAlignment(Element.ALIGN_MIDDLE);
        cQrOuter.setPaddingTop(14);
        cQrOuter.setPaddingRight(16);
        cQrOuter.setPaddingBottom(10);
        cQrOuter.setPaddingLeft(4);

        PdfPTable tQr = new PdfPTable(1);
        tQr.setWidthPercentage(100);
        PdfPCell cQrInner = new PdfPCell(qrImg, true);
        cQrInner.setBorder(Rectangle.BOX);
        cQrInner.setBorderColor(BLUE_PRIMARY);
        cQrInner.setBorderWidth(4f);
        cQrInner.setPadding(4);
        cQrInner.setBackgroundColor(WHITE);
        cQrInner.setHorizontalAlignment(Element.ALIGN_CENTER);
        tQr.addCell(cQrInner);
        cQrOuter.addElement(tQr);
        tPeserta.addCell(cQrOuter);

        cCard.addElement(tPeserta);

        // ── Strip biru keberangkatan ───────────────────────────────────────
        PdfPTable tStrip = new PdfPTable(1);
        tStrip.setWidthPercentage(100);
        PdfPCell cStrip = new PdfPCell(
                new Phrase("Keberangkatan : " + tgl, new Font(bfBold, 11, Font.NORMAL, WHITE)));
        cStrip.setHorizontalAlignment(Element.ALIGN_CENTER);
        cStrip.setBackgroundColor(BLUE_PRIMARY);
        cStrip.setBorder(Rectangle.NO_BORDER);
        cStrip.setPaddingTop(10);
        cStrip.setPaddingBottom(10);
        tStrip.addCell(cStrip);
        cCard.addElement(tStrip);

        // ── Rute & Armada ──────────────────────────────────────────────────
        PdfPTable tRute = new PdfPTable(2);
        tRute.setWidthPercentage(100);
        tRute.setWidths(new float[]{ 1f, 1f });

        PdfPCell cRute = new PdfPCell();
        cRute.setBorder(Rectangle.NO_BORDER);
        cRute.setBackgroundColor(WHITE);
        cRute.setPaddingLeft(16);
        cRute.setPaddingTop(14);
        cRute.setPaddingBottom(14);
        cRute.addElement(makeField(bf, bfBold, "Rute Perjalanan", rute));
        tRute.addCell(cRute);

        PdfPCell cArmada = new PdfPCell();
        cArmada.setBorder(Rectangle.NO_BORDER);
        cArmada.setBackgroundColor(WHITE);
        cArmada.setPaddingLeft(10);
        cArmada.setPaddingTop(14);
        cArmada.setPaddingBottom(14);
        cArmada.addElement(makeField(bf, bfBold, "Armada Bus", armada));
        tRute.addCell(cArmada);
        cCard.addElement(tRute);

        // ── Separator ─────────────────────────────────────────────────────
        PdfPTable tSep = new PdfPTable(1);
        tSep.setWidthPercentage(100);
        PdfPCell cSep = new PdfPCell(new Phrase(" "));
        cSep.setBorder(Rectangle.TOP);
        cSep.setBorderColor(SLATE_200);
        cSep.setBorderWidth(1f);
        cSep.setBackgroundColor(WHITE);
        cSep.setPaddingTop(2);
        cSep.setPaddingBottom(2);
        tSep.addCell(cSep);
        cCard.addElement(tSep);

        // ── Catatan & pengaduan ────────────────────────────────────────────
        Paragraph pNote = new Paragraph();
        pNote.setAlignment(Element.ALIGN_CENTER);
        pNote.setLeading(17f);
        pNote.add(new Chunk("*Harap datang 1 jam sebelum keberangkatan\n",
                new Font(bf, 9, Font.ITALIC, SLATE_500)));
        pNote.add(new Chunk("*Tunjukkan QR Code ini kepada petugas\n\n",
                new Font(bf, 9, Font.ITALIC, SLATE_500)));
        pNote.add(new Chunk("Layanan Pengaduan (WA) :\n",
                new Font(bf, 9, Font.NORMAL, SLATE_500)));
        pNote.add(new Chunk("08217653093 / 08217653095",
                new Font(bfBold, 12, Font.NORMAL, BLUE_PRIMARY)));

        PdfPCell cNote = new PdfPCell();
        cNote.setBorder(Rectangle.NO_BORDER);
        cNote.setBackgroundColor(WHITE);
        cNote.setHorizontalAlignment(Element.ALIGN_CENTER);
        cNote.setPaddingTop(14);
        cNote.setPaddingBottom(18);
        cNote.addElement(pNote);

        PdfPTable tNote = new PdfPTable(1);
        tNote.setWidthPercentage(100);
        tNote.addCell(cNote);
        cCard.addElement(tNote);

        tCard.addCell(cCard);
        document.add(tCard);

        // ════════════════════════════════════════════════════════════════════
        //  4. FOOTER
        // ════════════════════════════════════════════════════════════════════
        PdfPTable tFooter = new PdfPTable(1);
        tFooter.setWidthPercentage(100);
        tFooter.setSpacingBefore(8f);
        PdfPCell cFooter = new PdfPCell(
                new Phrase("Dinas Perhubungan Aceh  \u2022  Pemerintah Aceh 2026",
                        new Font(bf, 9, Font.NORMAL, BLUE_LIGHT_TEXT)));
        cFooter.setHorizontalAlignment(Element.ALIGN_CENTER);
        cFooter.setBorder(Rectangle.NO_BORDER);
        cFooter.setBackgroundColor(BG_DARK);
        cFooter.setPaddingTop(10);
        cFooter.setPaddingBottom(10);
        tFooter.addCell(cFooter);
        document.add(tFooter);

        document.close();
        return out.toByteArray();
    }

    // ════════════════════════════════════════════════════════════════════════
    //  HELPERS
    // ════════════════════════════════════════════════════════════════════════

    /**
     * Load logo dari classpath resources.
     * Taruh file PNG di src/main/resources/ dengan nama yang sama.
     * Return null jika file tidak ditemukan (fallback ke teks).
     */
    private Image loadLogo(String resourcePath) {
        try {
            InputStream is = getClass().getResourceAsStream(resourcePath);
            if (is == null) return null;
            byte[] bytes = is.readAllBytes();
            is.close();
            return Image.getInstance(bytes);
        } catch (Exception e) {
            return null;
        }
    }

    private Paragraph makeField(BaseFont bf, BaseFont bfBold, String label, String value) {
        Paragraph p = new Paragraph();
        p.setLeading(15f);
        p.add(new Chunk(label + "\n",                new Font(bf,     9,  Font.NORMAL, BLUE_LABEL)));
        p.add(new Chunk(value != null ? value : "-", new Font(bfBold, 13, Font.NORMAL, SLATE_900)));
        return p;
    }

    private Paragraph makeSpacer(float height) {
        Paragraph p = new Paragraph(" ");
        p.setLeading(height);
        return p;
    }

    private Image generateQRCodeImage(String text, int size) throws Exception {
        QRCodeWriter writer = new QRCodeWriter();
        BitMatrix matrix = writer.encode(text, BarcodeFormat.QR_CODE, size, size);
        ByteArrayOutputStream png = new ByteArrayOutputStream();
        MatrixToImageWriter.writeToStream(matrix, "PNG", png);
        return Image.getInstance(png.toByteArray());
    }

    private String nvl(String s) {
        return s != null ? s : "-";
    }
}