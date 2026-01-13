package com.mudik.service;

import com.lowagie.text.*;
import com.lowagie.text.pdf.PdfWriter;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.mudik.model.PendaftaranMudik;
import jakarta.enterprise.context.ApplicationScoped;

import java.io.ByteArrayOutputStream;

@ApplicationScoped
public class TiketService {

    public byte[] cetakTiket(PendaftaranMudik pendaftar) throws Exception {

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document document = new Document(PageSize.A4);
        PdfWriter.getInstance(document, out);

        document.open();

        // 2. Bikin JUDUL
        Font fontJudul = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 24);
        Paragraph judul = new Paragraph("E-TIKET MUDIK GRATIS 2025", fontJudul);
        judul.setAlignment(Element.ALIGN_CENTER);
        judul.setSpacingAfter(20);
        document.add(judul);

        // 3. Garis Pemisah
        document.add(new Paragraph("----------------------------------------------------------------------------------------------------------------"));

        // 4. Data Penumpang (Font Biasa)
        Font fontIsi = FontFactory.getFont(FontFactory.HELVETICA, 14);

        document.add(new Paragraph("\nDATA PENUMPANG:", FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14)));
        document.add(new Paragraph("Nama Peserta : " + pendaftar.nama_peserta, fontIsi));
        document.add(new Paragraph("NIK          : " + pendaftar.nik_peserta, fontIsi));
        document.add(new Paragraph("Status       : " + pendaftar.status_pendaftaran, fontIsi));

        document.add(new Paragraph("\nDETAIL PERJALANAN:", FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14)));
        document.add(new Paragraph("Tujuan       : " + pendaftar.rute.tujuan, fontIsi));
        document.add(new Paragraph("Kode Barang  : " + pendaftar.kode_token_barang, fontIsi)); // <--- PENTING

        // 5. Bikin QR CODE (Isinya adalah kode_token_barang)
        // Kalau discan nanti muncul teks: "SIG-5-101" (Misalnya)
        Image qrImage = generateQRCodeImage(pendaftar.kode_token_barang);
        qrImage.setAlignment(Element.ALIGN_CENTER);
        qrImage.setSpacingBefore(30);
        document.add(qrImage);

        // 6. Footer
        Paragraph footer = new Paragraph("\n\nHarap tunjukkan QR Code ini kepada petugas saat keberangkatan.", FontFactory.getFont(FontFactory.HELVETICA_OBLIQUE, 10));
        footer.setAlignment(Element.ALIGN_CENTER);
        document.add(footer);

        document.close();
        return out.toByteArray();
    }

    // Fungsi Pembantu: Bikin Gambar QR
    private Image generateQRCodeImage(String text) throws Exception {
        QRCodeWriter barcodeWriter = new QRCodeWriter();
        // Bikin matriks QR Code ukuran 200x200 pixel
        BitMatrix bitMatrix = barcodeWriter.encode(text, BarcodeFormat.QR_CODE, 200, 200);

        // Konversi jadi Gambar PNG
        ByteArrayOutputStream pngOutputStream = new ByteArrayOutputStream();
        MatrixToImageWriter.writeToStream(bitMatrix, "PNG", pngOutputStream);

        // Masukkan ke format PDF Image
        return Image.getInstance(pngOutputStream.toByteArray());
    }
}