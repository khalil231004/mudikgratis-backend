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

    // ── Warna ──────────────────────────────────────────────────────────────
    private static final Color BLUE_PRIMARY  = new Color(0x25, 0x63, 0xEB);
    private static final Color BLUE_LABEL    = new Color(0x29, 0x78, 0xFF);
    private static final Color WHITE         = Color.WHITE;
    private static final Color SLATE_900     = new Color(0x0F, 0x17, 0x2A);
    private static final Color SLATE_500     = new Color(0x64, 0x74, 0x8B);
    private static final Color SLATE_200     = new Color(0xE2, 0xE8, 0xF0);
    private static final Color BG_DARK       = new Color(0x05, 0x0D, 0x1F);
    private static final Color BLUE_LIGHT_TEXT = new Color(0x93, 0xC5, 0xFD);

    public byte[] cetakTiket(PendaftaranMudik pendaftar) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        Document document = new Document(PageSize.A4, 36, 36, 36, 36);
        PdfWriter.getInstance(document, out);
        document.open();

        // ── DATA ────────────────────────────────────────────────────────────
        String kode    = pendaftar.kode_booking != null ? pendaftar.kode_booking : "MDK-" + pendaftar.pendaftaran_id;
        String nama    = pendaftar.nama_peserta  != null ? pendaftar.nama_peserta.toUpperCase() : "-";
        String nik     = pendaftar.nik_peserta   != null ? pendaftar.nik_peserta  : "-";
        String alamat  = pendaftar.alamat_rumah  != null ? pendaftar.alamat_rumah : "-";
        String tgl     = pendaftar.rute != null ? pendaftar.rute.getFormattedDate() : "Jadwal Belum Rilis";
        String rute    = pendaftar.rute != null
                ? nvl(pendaftar.rute.asal) + " - " + nvl(pendaftar.rute.tujuan)
                : "-";
        String armada  = (pendaftar.kendaraan != null && pendaftar.kendaraan.nama_armada != null)
                ? pendaftar.kendaraan.nama_armada : "-";

        // ── FONTS ────────────────────────────────────────────────────────────
        BaseFont bf     = BaseFont.createFont(BaseFont.HELVETICA,      BaseFont.WINANSI, false);
        BaseFont bfBold = BaseFont.createFont(BaseFont.HELVETICA_BOLD, BaseFont.WINANSI, false);

        // ════════════════════════════════════════════════════════════════════
        //  1. HEADER: Logo kiri & kanan (background gelap)
        // ════════════════════════════════════════════════════════════════════
        PdfPTable tHeader = new PdfPTable(3);
        tHeader.setWidthPercentage(100);
        tHeader.setWidths(new float[]{ 3f, 4f, 3f });
        tHeader.setSpacingAfter(0f);

        tHeader.addCell(makeHeaderCell(bf, bfBold, "DISHUB ACEH", "Pemerintah Aceh", Element.ALIGN_LEFT));
        PdfPCell cMid = new PdfPCell(new Phrase(" "));
        cMid.setBorder(Rectangle.NO_BORDER);
        cMid.setBackgroundColor(BG_DARK);
        tHeader.addCell(cMid);
        tHeader.addCell(makeHeaderCell(bf, bfBold, "Mudik Gratis", "Pemerintah Aceh", Element.ALIGN_RIGHT));
        document.add(tHeader);

        // ════════════════════════════════════════════════════════════════════
        //  2. JUDUL E-TIKET
        // ════════════════════════════════════════════════════════════════════
        PdfPTable tJudul = new PdfPTable(1);
        tJudul.setWidthPercentage(100);
        tJudul.setSpacingAfter(8f);

        Paragraph pJudul = new Paragraph();
        pJudul.setAlignment(Element.ALIGN_CENTER);
        pJudul.add(new Chunk("E-TIKET\n",              new Font(bfBold, 56, Font.NORMAL, WHITE)));
        pJudul.add(new Chunk("MUDIK GRATIS\n",         new Font(bfBold, 13, Font.NORMAL, BLUE_LIGHT_TEXT)));
        pJudul.add(new Chunk("PEMERINTAH ACEH 2026",   new Font(bfBold, 13, Font.NORMAL, BLUE_LIGHT_TEXT)));

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

        // Kolom kiri
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

        // Kolom kanan: QR
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

        // Wrap QR dalam tabel ber-border biru
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

        // ── Garis putus-putus separator ────────────────────────────────────
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

        // ── Catatan & nomor pengaduan ──────────────────────────────────────
        PdfPTable tNote = new PdfPTable(1);
        tNote.setWidthPercentage(100);

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

    private PdfPCell makeHeaderCell(BaseFont bf, BaseFont bfBold, String title, String sub, int align) {
        Paragraph p = new Paragraph();
        p.setAlignment(align);
        p.setLeading(15f);
        p.add(new Chunk(title + "\n", new Font(bfBold, 10, Font.NORMAL, WHITE)));
        p.add(new Chunk(sub,          new Font(bf,     8,  Font.NORMAL, BLUE_LIGHT_TEXT)));
        PdfPCell cell = new PdfPCell();
        cell.setBorder(Rectangle.NO_BORDER);
        cell.setBackgroundColor(BG_DARK);
        cell.setHorizontalAlignment(align);
        cell.setPaddingTop(8);
        cell.setPaddingBottom(8);
        cell.setPaddingLeft(align == Element.ALIGN_LEFT ? 4 : 0);
        cell.setPaddingRight(align == Element.ALIGN_RIGHT ? 4 : 0);
        cell.addElement(p);
        return cell;
    }

    /** Label biru kecil + value bold gelap */
    private Paragraph makeField(BaseFont bf, BaseFont bfBold, String label, String value) {
        Paragraph p = new Paragraph();
        p.setLeading(15f);
        p.add(new Chunk(label + "\n",           new Font(bf,     9,  Font.NORMAL, BLUE_LABEL)));
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