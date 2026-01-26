package com.mudik.service;

import com.mudik.model.PendaftaranMudik;
import jakarta.enterprise.context.ApplicationScoped;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@ApplicationScoped
public class ExcelService {

    public byte[] generateLaporanExcel(List<PendaftaranMudik> dataList) throws IOException {
        try (Workbook workbook = new XSSFWorkbook()) {

            // 1. KELOMPOKKAN DATA BERDASARKAN RUTE
            // Hasilnya: Map<"Medan", List<Penumpang>>
            Map<String, List<PendaftaranMudik>> dataPerRute = dataList.stream()
                    .collect(Collectors.groupingBy(p -> (p.rute != null ? p.rute.tujuan : "Tanpa Rute")));

            // 2. BIKIN SHEET UNTUK SETIAP RUTE
            for (Map.Entry<String, List<PendaftaranMudik>> entry : dataPerRute.entrySet()) {
                String namaRute = entry.getKey();
                List<PendaftaranMudik> listPenumpang = entry.getValue();

                // Bersihkan nama sheet dari karakter terlarang Excel (:/ \ ? * [ ])
                String safeSheetName = namaRute.replaceAll("[:/\\\\?*\\[\\]]", " ").trim();
                if (safeSheetName.length() > 30) safeSheetName = safeSheetName.substring(0, 30); // Max 31 chars

                // Kalau nama sheet duplikat, tambah angka dikit (jarang terjadi sih)
                Sheet sheet = workbook.createSheet(safeSheetName);

                buatIsiSheet(workbook, sheet, namaRute, listPenumpang);
            }

            // Write to Byte Array
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);
            return out.toByteArray();
        }
    }

    private void buatIsiSheet(Workbook workbook, Sheet sheet, String namaRute, List<PendaftaranMudik> list) {
        // --- STYLE ---
        CellStyle headerStyle = createHeaderStyle(workbook);
        CellStyle boldStyle = workbook.createCellStyle();
        Font f = workbook.createFont(); f.setBold(true); boldStyle.setFont(f);

        // --- 1. JUDUL BESAR & REKAP KATEGORI (Permintaan Klien) ---
        // Hitung Kategori
        long total = list.size();
        long dewasa = list.stream().filter(p -> "DEWASA".equalsIgnoreCase(p.kategori_penumpang)).count();
        long anak = list.stream().filter(p -> "ANAK".equalsIgnoreCase(p.kategori_penumpang)).count();
        long bayi = list.stream().filter(p -> "BAYI".equalsIgnoreCase(p.kategori_penumpang)).count();

        // Judul Rute
        Row rowJudul = sheet.createRow(0);
        Cell cellJudul = rowJudul.createCell(0);
        cellJudul.setCellValue("LAPORAN MUDIK: " + namaRute.toUpperCase());
        cellJudul.setCellStyle(boldStyle);

        // Tabel Rekap Kecil di Atas
        sheet.createRow(1).createCell(0).setCellValue("Ringkasan Penumpang:");

        Row rowRekapHead = sheet.createRow(2);
        rowRekapHead.createCell(0).setCellValue("Total");
        rowRekapHead.createCell(1).setCellValue("Dewasa");
        rowRekapHead.createCell(2).setCellValue("Anak");
        rowRekapHead.createCell(3).setCellValue("Bayi");
        // Style Header Rekap
        for(int i=0; i<=3; i++) rowRekapHead.getCell(i).setCellStyle(headerStyle);

        Row rowRekapVal = sheet.createRow(3);
        rowRekapVal.createCell(0).setCellValue(total);
        rowRekapVal.createCell(1).setCellValue(dewasa);
        rowRekapVal.createCell(2).setCellValue(anak);
        rowRekapVal.createCell(3).setCellValue(bayi);

        // --- 2. HEADER TABEL DATA ---
        int startRow = 5; // Mulai baris ke-6
        Row headerRow = sheet.createRow(startRow);
        String[] columns = {"No", "Nama Peserta", "NIK", "Kategori", "JK", "Titik Jemput", "No HP", "Kode Booking", "Status"};

        for (int i = 0; i < columns.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(columns[i]);
            cell.setCellStyle(headerStyle);
        }

        // --- 3. ISI DATA PENUMPANG ---
        int rowNum = startRow + 1;
        for (PendaftaranMudik p : list) {
            Row row = sheet.createRow(rowNum++);

            row.createCell(0).setCellValue(rowNum - startRow - 1);
            row.createCell(1).setCellValue(p.nama_peserta);
            row.createCell(2).setCellValue(p.nik_peserta);
            row.createCell(3).setCellValue(p.kategori_penumpang); // PENTING: Kategori
            row.createCell(4).setCellValue(p.jenis_kelamin);
            row.createCell(5).setCellValue(p.titik_jemput);

            // Logic HP
            String hp = (p.no_hp_peserta != null && p.no_hp_peserta.length() > 5)
                    ? p.no_hp_peserta
                    : ((p.user != null) ? p.user.no_hp : "-");
            row.createCell(6).setCellValue(hp);

            row.createCell(7).setCellValue(p.kode_booking != null ? p.kode_booking : "-");
            row.createCell(8).setCellValue(p.status_pendaftaran);
        }

        // Auto Size Columns
        for (int i = 0; i < columns.length; i++) {
            sheet.autoSizeColumn(i);
        }
    }

    private CellStyle createHeaderStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        return style;
    }
}