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
import java.util.Locale;

@ApplicationScoped
public class TiketService {

    public byte[] cetakTiket(PendaftaranMudik pendaftar) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document document = new Document(PageSize.A4);
        PdfWriter.getInstance(document, out);

        document.open();

        // 1. KODE BOOKING
        String kodeUnik = (pendaftar.kode_booking != null) ? pendaftar.kode_booking : "MDK-ERR-" + pendaftar.pendaftaran_id;

        // --- HEADER ---
        Font fontJudul = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 24, Color.BLUE);
        Paragraph judul = new Paragraph("E-TIKET MUDIK GRATIS 2026", fontJudul);
        judul.setAlignment(Element.ALIGN_CENTER);
        judul.setSpacingAfter(5);
        document.add(judul);

        Font fontSub = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14, Color.RED);
        Paragraph sub = new Paragraph("KODE BOOKING: " + kodeUnik, fontSub);
        sub.setAlignment(Element.ALIGN_CENTER);
        sub.setSpacingAfter(15);
        document.add(sub);

        document.add(new Paragraph("----------------------------------------------------------------------------------------------------------------"));

        PdfPTable table = new PdfPTable(2);
        table.setWidthPercentage(100);
        table.setSpacingBefore(20f);
        table.setSpacingAfter(20f);
        table.setWidths(new float[]{1, 2});

        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd MMMM yyyy", new Locale("id", "ID"));

        // Isi Data
        addCell(table, "Nama Penumpang", pendaftar.nama_peserta);
        addCell(table, "NIK", pendaftar.nik_peserta);

        String ruteInfo = (pendaftar.rute != null) ? pendaftar.rute.asal + " ➜ " + pendaftar.rute.tujuan : "Data Rute Hilang";
        addCell(table, "Rute Perjalanan", ruteInfo);

        addCell(table, "Titik Jemput", pendaftar.titik_jemput); // PENTING: Lokasi jemput


        String infoBarang = (pendaftar.berat_barang + " Kg") + " / " + pendaftar.ukuran_barang;
        addCell(table, "Barang Bawaan", infoBarang);

        addCell(table, "Status Tiket", pendaftar.status_pendaftaran);

        document.add(table);

        // --- QR CODE (PENTING BUAT SCANNER) ---
        // QR Code isinya JSON String simple biar nanti petugas gampang scan
        String qrData = "MUDIK|" + kodeUnik + "|" + pendaftar.nik_peserta;
        Image qrImage = generateQRCodeImage(qrData);
        qrImage.setAlignment(Element.ALIGN_CENTER);
        qrImage.scalePercent(150f); // Perbesar dikit
        document.add(qrImage);

        // --- FOOTER ---
        Paragraph footer = new Paragraph("\n*Harap datang 1 jam sebelum keberangkatan.\n*Tunjukkan QR Code ini kepada petugas saat check-in.", FontFactory.getFont(FontFactory.HELVETICA_OBLIQUE, 10));
        footer.setAlignment(Element.ALIGN_CENTER);
        document.add(footer);

        document.close();
        return out.toByteArray();
    }

    private void addCell(PdfPTable table, String header, String value) {
        Font fontHeader = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12, Color.DARK_GRAY);
        PdfPCell cellHeader = new PdfPCell(new Paragraph(header, fontHeader));
        cellHeader.setBackgroundColor(new Color(230, 230, 230)); // Abu muda
        cellHeader.setPadding(8);
        cellHeader.setBorderColor(Color.GRAY);
        table.addCell(cellHeader);

        PdfPCell cellValue = new PdfPCell(new Paragraph(value != null ? value : "-", FontFactory.getFont(FontFactory.HELVETICA, 12)));
        cellValue.setPadding(8);
        cellValue.setBorderColor(Color.GRAY);
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