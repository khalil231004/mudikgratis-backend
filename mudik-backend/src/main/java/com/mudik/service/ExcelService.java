package com.mudik.service;

import com.mudik.model.PendaftaranMudik;
import jakarta.enterprise.context.ApplicationScoped;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
// ... imports tetap sama ...
import java.time.format.DateTimeFormatter;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@ApplicationScoped
public class ExcelService {

    public byte[] generateLaporanExcel(List<PendaftaranMudik> dataList) throws IOException {
        try (Workbook workbook = new XSSFWorkbook()) {

            Map<String, List<PendaftaranMudik>> dataPerRute = dataList.stream()
                    .collect(Collectors.groupingBy(p -> (p.rute != null ? p.rute.tujuan : "Tanpa Rute")));

            for (Map.Entry<String, List<PendaftaranMudik>> entry : dataPerRute.entrySet()) {
                String namaRute = entry.getKey();
                List<PendaftaranMudik> listPenumpang = entry.getValue();

                String safeSheetName = namaRute.replaceAll("[:/\\\\?*\\[\\]]", " ").trim();
                if (safeSheetName.length() > 30) safeSheetName = safeSheetName.substring(0, 30);

                Sheet sheet = workbook.createSheet(safeSheetName);
                buatIsiSheet(workbook, sheet, namaRute, listPenumpang);
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);
            return out.toByteArray();
        }
    }

    private void buatIsiSheet(Workbook workbook, Sheet sheet, String namaRute, List<PendaftaranMudik> list) {

        CellStyle headerStyle = createHeaderStyle(workbook);

        CellStyle boldStyle = workbook.createCellStyle();
        Font f = workbook.createFont();
        f.setBold(true);
        boldStyle.setFont(f);

        CellStyle statusOkStyle = createStatusStyle(workbook, IndexedColors.GREEN);
        CellStyle statusFailStyle = createStatusStyle(workbook, IndexedColors.RED);
        CellStyle statusWaitStyle = createStatusStyle(workbook, IndexedColors.ORANGE);

        long total = list.size();

        long dewasa = list.stream().filter(p -> "DEWASA".equalsIgnoreCase(p.kategori_penumpang)).count();
        long anak = list.stream().filter(p -> "ANAK".equalsIgnoreCase(p.kategori_penumpang)).count();
        long bayi = list.stream().filter(p -> "BAYI".equalsIgnoreCase(p.kategori_penumpang)).count();

        long sukses = list.stream().filter(p -> "DITERIMA".equalsIgnoreCase(p.status_pendaftaran)).count();
        long menunggu = list.stream().filter(p -> "MENUNGGU_VERIFIKASI".equalsIgnoreCase(p.status_pendaftaran)).count();
        long gagal = list.stream().filter(p ->
                "DITOLAK".equalsIgnoreCase(p.status_pendaftaran) ||
                        "DIBATALKAN".equalsIgnoreCase(p.status_pendaftaran)
        ).count();


        Row rowJudul = sheet.createRow(0);
        Cell cellJudul = rowJudul.createCell(0);
        cellJudul.setCellValue("LAPORAN MUDIK: " + namaRute.toUpperCase());
        cellJudul.setCellStyle(boldStyle);


        sheet.createRow(1).createCell(0).setCellValue("Ringkasan Kategori (Untuk Bus):");

        Row rowHead1 = sheet.createRow(2);
        rowHead1.createCell(0).setCellValue("Total");
        rowHead1.createCell(1).setCellValue("Dewasa");
        rowHead1.createCell(2).setCellValue("Anak");
        rowHead1.createCell(3).setCellValue("Bayi");
        for (int i = 0; i <= 3; i++) rowHead1.getCell(i).setCellStyle(headerStyle);

        Row rowVal1 = sheet.createRow(3);
        rowVal1.createCell(0).setCellValue(total);
        rowVal1.createCell(1).setCellValue(dewasa);
        rowVal1.createCell(2).setCellValue(anak);
        rowVal1.createCell(3).setCellValue(bayi);


        sheet.getRow(1).createCell(5).setCellValue("Ringkasan Status (Laporan Klien):");

        Row rowHead2 = sheet.getRow(2);
        rowHead2.createCell(5).setCellValue("DITERIMA");
        rowHead2.createCell(6).setCellValue("MENUNGGU");
        rowHead2.createCell(7).setCellValue("GAGAL (Tolak/Batal)");
        for (int i = 5; i <= 7; i++) rowHead2.getCell(i).setCellStyle(headerStyle);

        Row rowVal2 = sheet.getRow(3);
        rowVal2.createCell(5).setCellValue(sukses);
        rowVal2.createCell(6).setCellValue(menunggu);
        rowVal2.createCell(7).setCellValue(gagal);

        int startRow = 6;
        Row headerRow = sheet.createRow(startRow);
        String[] columns = {"No", "Nama Peserta", "NIK", "Kategori", "JK", "Titik Jemput", "No HP", "Kode Booking", "Status"};

        for (int i = 0; i < columns.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(columns[i]);
            cell.setCellStyle(headerStyle);
        }

        int rowNum = startRow + 1;
        for (PendaftaranMudik p : list) {
            Row row = sheet.createRow(rowNum++);

            row.createCell(0).setCellValue(rowNum - startRow - 1);
            row.createCell(1).setCellValue(p.nama_peserta);
            row.createCell(2).setCellValue(p.nik_peserta);
            row.createCell(3).setCellValue(p.kategori_penumpang);
            row.createCell(4).setCellValue(p.jenis_kelamin);
            row.createCell(5).setCellValue(p.alamat_rumah);

            String hp = (p.no_hp_peserta != null && p.no_hp_peserta.length() > 5)
                    ? p.no_hp_peserta
                    : ((p.user != null) ? p.user.no_hp : "-");
            row.createCell(6).setCellValue(hp);

            row.createCell(7).setCellValue(p.kode_booking != null ? p.kode_booking : "-");

            Cell cellStatus = row.createCell(8);
            String status = p.status_pendaftaran;
            cellStatus.setCellValue(status);

            if ("DITERIMA".equalsIgnoreCase(status)) {
                cellStatus.setCellStyle(statusOkStyle);
            } else if ("DITOLAK".equalsIgnoreCase(status) || "DIBATALKAN".equalsIgnoreCase(status)) {
                cellStatus.setCellStyle(statusFailStyle);
            } else {
                cellStatus.setCellStyle(statusWaitStyle);
            }
        }

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

    private CellStyle createStatusStyle(Workbook workbook, IndexedColors color) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setColor(color.getIndex());
        font.setBold(true);
        style.setFont(font);
        return style;
    }
}