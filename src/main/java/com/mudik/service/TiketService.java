package com.mudik.service;

import com.lowagie.text.*;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.mudik.model.PendaftaranMudik;
import jakarta.enterprise.context.ApplicationScoped;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.time.format.DateTimeFormatter;

@ApplicationScoped
public class TiketService {

    public byte[] cetakTiket(PendaftaranMudik pendaftar) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document document = new Document(PageSize.A4);
        PdfWriter.getInstance(document, out);

        document.open();

        // 1. HEADER
        String kodeUnik = (pendaftar.kode_booking != null) ? pendaftar.kode_booking : "MDK-" + pendaftar.pendaftaran_id;

        Font fontJudul = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 22, new Color(0, 51, 102));
        Paragraph judul = new Paragraph("E-TIKET MUDIK GRATIS 2026", fontJudul);
        judul.setAlignment(Element.ALIGN_CENTER);
        document.add(judul);

        Font fontSub = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16, Color.RED);
        Paragraph sub = new Paragraph(kodeUnik, fontSub);
        sub.setAlignment(Element.ALIGN_CENTER);
        sub.setSpacingAfter(20);
        document.add(sub);

        document.add(new Paragraph("----------------------------------------------------------------------------------------------------------------"));

        // 2. TANGGAL KEBERANGKATAN
        String tglBerangkat = (pendaftar.rute != null) ? pendaftar.rute.getFormattedDate() : "Jadwal Belum Rilis";

        Font fontPenting = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14, Color.BLACK);
        Paragraph pTgl = new Paragraph("KEBERANGKATAN:\n" + tglBerangkat, fontPenting);
        pTgl.setAlignment(Element.ALIGN_CENTER);
        pTgl.setSpacingBefore(10);
        pTgl.setSpacingAfter(10);
        document.add(pTgl);

        // 3. TABEL DETAIL
        PdfPTable table = new PdfPTable(2);
        table.setWidthPercentage(100);
        table.setSpacingBefore(10f);
        table.setSpacingAfter(20f);
        table.setWidths(new float[]{1.5f, 2.5f});

        // --- ISI DATA ---
        addCell(table, "Nama Penumpang", pendaftar.nama_peserta);
        addCell(table, "NIK", pendaftar.nik_peserta);

        String ruteInfo = (pendaftar.rute != null) ? pendaftar.rute.asal + " ➜ " + pendaftar.rute.tujuan : "-";
        addCell(table, "Rute Perjalanan", ruteInfo);

        // 🔥 INFO BUS (PASTI ADA KARENA SUDAH DICEK DI RESOURCE) 🔥
        String namaBus = (pendaftar.kendaraan != null) ? pendaftar.kendaraan.nama_armada : "ERROR";
        String plat = (pendaftar.kendaraan != null) ? pendaftar.kendaraan.plat_nomor : "-";

        // Kita bold biar kelihatan jelas
        addCell(table, "ARMADA BUS", namaBus + "\n(" + plat + ")");

        addCell(table, "Alamat Domisili", pendaftar.alamat_rumah);

        String infoBarang = (pendaftar.jenis_barang != null ? pendaftar.jenis_barang : "Tidak Bawa Barang")
                + " (" + (pendaftar.ukuran_barang != null ? pendaftar.ukuran_barang : "-") + ")";
        addCell(table, "Barang Bawaan", infoBarang);

        document.add(table);

        // 4. QR CODE
        String qrData = kodeUnik + ";" + pendaftar.nik_peserta + ";BUS:" + plat;
        Image qrImage = generateQRCodeImage(qrData);
        qrImage.setAlignment(Element.ALIGN_CENTER);
        qrImage.scalePercent(120f);
        document.add(qrImage);

        // Footer
        Paragraph footer = new Paragraph("\n*Harap datang 1 jam sebelum keberangkatan.\n*Tunjukkan QR Code ini kepada petugas.",
                FontFactory.getFont(FontFactory.HELVETICA_OBLIQUE, 10));
        footer.setAlignment(Element.ALIGN_CENTER);
        document.add(footer);

        document.close();
        return out.toByteArray();
    }

    private void addCell(PdfPTable table, String header, String value) {
        Font fontHeader = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12, Color.DARK_GRAY);
        PdfPCell cellHeader = new PdfPCell(new Paragraph(header, fontHeader));
        cellHeader.setBackgroundColor(new Color(240, 240, 240));
        cellHeader.setPadding(8);
        cellHeader.setBorderColor(Color.LIGHT_GRAY);
        table.addCell(cellHeader);

        PdfPCell cellValue = new PdfPCell(new Paragraph(value != null ? value : "-", FontFactory.getFont(FontFactory.HELVETICA, 12)));
        cellValue.setPadding(8);
        cellValue.setBorderColor(Color.LIGHT_GRAY);
        table.addCell(cellValue);
    }

    private Image generateQRCodeImage(String text) throws Exception {
        QRCodeWriter barcodeWriter = new QRCodeWriter();
        BitMatrix bitMatrix = barcodeWriter.encode(text, BarcodeFormat.QR_CODE, 300, 300);
        ByteArrayOutputStream pngOutputStream = new ByteArrayOutputStream();
        MatrixToImageWriter.writeToStream(bitMatrix, "PNG", pngOutputStream);
        return Image.getInstance(pngOutputStream.toByteArray());
    }
}