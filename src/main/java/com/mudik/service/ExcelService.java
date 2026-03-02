package com.mudik.service;

import com.mudik.model.PendaftaranMudik;
import com.mudik.model.Kendaraan;
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

            // ── Sheet 1: RINGKASAN semua rute & bus ───────────────────────────────
            buatSheetRingkasan(workbook, dataList);

            // ── Sheet per-Rute ────────────────────────────────────────────────────
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

    // ── Sheet Ringkasan: rekap semua rute + bus ───────────────────────────
    private void buatSheetRingkasan(Workbook workbook, List<PendaftaranMudik> dataList) {
        Sheet sheet = workbook.createSheet("RINGKASAN");
        CellStyle headerStyle = createHeaderStyle(workbook);
        CellStyle boldStyle   = workbook.createCellStyle();
        Font f = workbook.createFont(); f.setBold(true); boldStyle.setFont(f);

        int r = 0;

        // ── Judul
        Row rowJudul = sheet.createRow(r++);
        Cell cJudul = rowJudul.createCell(0);
        cJudul.setCellValue("REKAP MUDIK GRATIS — SEMUA RUTE");
        cJudul.setCellStyle(boldStyle);
        r++;

        // ── Tabel Rekap Per Rute
        Row hRute = sheet.createRow(r++);
        String[] ruteH = {"No", "Rute Tujuan", "Total Daftar", "Diterima H-3", "Siap Berangkat", "Ditolak", "Dibatalkan", "Menunggu", "Dewasa", "Anak", "Bayi"};
        for (int i = 0; i < ruteH.length; i++) { Cell c = hRute.createCell(i); c.setCellValue(ruteH[i]); c.setCellStyle(headerStyle); }

        Map<String, List<PendaftaranMudik>> perRute = dataList.stream()
                .collect(java.util.stream.Collectors.groupingBy(p -> p.rute != null ? (p.rute.asal + " → " + p.rute.tujuan) : "Tanpa Rute"));

        int no = 1;
        for (Map.Entry<String, List<PendaftaranMudik>> e : perRute.entrySet()) {
            List<PendaftaranMudik> rL = e.getValue();
            Row row = sheet.createRow(r++);
            row.createCell(0).setCellValue(no++);
            row.createCell(1).setCellValue(e.getKey());
            row.createCell(2).setCellValue(rL.size());
            row.createCell(3).setCellValue(rL.stream().filter(p -> "DITERIMA H-3".equals(p.status_pendaftaran)).count());
            row.createCell(4).setCellValue(rL.stream().filter(p -> "TERVERIFIKASI/ SIAP BERANGKAT".equals(p.status_pendaftaran)).count());
            row.createCell(5).setCellValue(rL.stream().filter(p -> "DITOLAK".equals(p.status_pendaftaran)).count());
            row.createCell(6).setCellValue(rL.stream().filter(p -> "DIBATALKAN".equals(p.status_pendaftaran)).count());
            row.createCell(7).setCellValue(rL.stream().filter(p -> "MENUNGGU VERIFIKASI".equals(p.status_pendaftaran)).count());
            row.createCell(8).setCellValue(rL.stream().filter(p -> "DEWASA".equalsIgnoreCase(p.kategori_penumpang)).count());
            row.createCell(9).setCellValue(rL.stream().filter(p -> "ANAK".equalsIgnoreCase(p.kategori_penumpang)).count());
            row.createCell(10).setCellValue(rL.stream().filter(p -> "BAYI".equalsIgnoreCase(p.kategori_penumpang)).count());
        }
        r += 2;

        // ── Tabel Rekap Per Bus
        Row hBus = sheet.createRow(r++);
        String[] busH = {"No", "Nama Armada", "Rute", "Plat Nomor", "Nama Supir", "Kapasitas", "Terisi", "Sisa"};
        for (int i = 0; i < busH.length; i++) { Cell c = hBus.createCell(i); c.setCellValue(busH[i]); c.setCellStyle(headerStyle); }

        // Kumpulkan data bus unik dari dataList
        Map<String, com.mudik.model.Kendaraan> busMap = new java.util.LinkedHashMap<>();
        for (PendaftaranMudik p : dataList) {
            if (p.kendaraan != null && p.kendaraan.nama_armada != null) {
                busMap.putIfAbsent(p.kendaraan.nama_armada, p.kendaraan);
            }
        }

        int noBus = 1;
        for (Map.Entry<String, com.mudik.model.Kendaraan> e : busMap.entrySet()) {
            com.mudik.model.Kendaraan bus = e.getValue();
            Row row = sheet.createRow(r++);
            int kap = bus.kapasitas_total != null ? bus.kapasitas_total : 0;
            int ter = bus.terisi != null ? bus.terisi : 0;
            row.createCell(0).setCellValue(noBus++);
            row.createCell(1).setCellValue(bus.nama_armada != null ? bus.nama_armada : "-");
            row.createCell(2).setCellValue(bus.rute != null ? (bus.rute.asal + " → " + bus.rute.tujuan) : "-");
            row.createCell(3).setCellValue(bus.plat_nomor != null ? bus.plat_nomor : "-");
            row.createCell(4).setCellValue(bus.nama_supir != null ? bus.nama_supir : "-");
            row.createCell(5).setCellValue(kap);
            row.createCell(6).setCellValue(ter);
            row.createCell(7).setCellValue(kap - ter);
        }

        for (int i = 0; i < 11; i++) sheet.autoSizeColumn(i);
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
        // FIX: tambah kolom Bus Plotting sesuai assign kendaraan
        String[] columns = {"No", "Nama Peserta", "NIK", "Kategori", "JK", "Titik Jemput", "No HP", "Kode Booking", "Status", "Bus Plotting", "Rute"};

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

            // FIX: isi kolom Bus Plotting sesuai kendaraan yang di-assign
            String namaArmada = (p.kendaraan != null && p.kendaraan.nama_armada != null)
                    ? p.kendaraan.nama_armada : "Belum Plotting";
            row.createCell(9).setCellValue(namaArmada);

            // Kolom Rute
            String namaRuteCell = (p.rute != null)
                    ? (p.rute.asal != null ? p.rute.asal : "Banda Aceh") + " → " + p.rute.tujuan
                    : "-";
            row.createCell(10).setCellValue(namaRuteCell);
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

    // FIX 5: Generate Manifest Per Bus
    public byte[] generateManifestBus(Kendaraan bus, List<PendaftaranMudik> dataList) throws IOException {
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Manifest " + (bus.nama_armada != null ? bus.nama_armada : "Bus"));
            CellStyle headerStyle = createHeaderStyle(workbook);
            CellStyle boldStyle = workbook.createCellStyle();
            Font f = workbook.createFont(); f.setBold(true); boldStyle.setFont(f);

            // Header Info Bus
            int r = 0;
            Row rowJudul = sheet.createRow(r++);
            Cell cellJudul = rowJudul.createCell(0);
            cellJudul.setCellValue("MANIFEST PENUMPANG BUS: " + (bus.nama_armada != null ? bus.nama_armada.toUpperCase() : "-"));
            cellJudul.setCellStyle(boldStyle);

            sheet.createRow(r).createCell(0).setCellValue("Plat Nomor: " + (bus.plat_nomor != null ? bus.plat_nomor : "-"));
            sheet.getRow(r++).createCell(3).setCellValue("Nama Supir: " + (bus.nama_supir != null ? bus.nama_supir : "-"));
            sheet.createRow(r).createCell(0).setCellValue("Kapasitas: " + (bus.kapasitas_total != null ? bus.kapasitas_total : 0));
            sheet.getRow(r++).createCell(3).setCellValue("Kontak Supir: " + (bus.kontak_supir != null ? bus.kontak_supir : "-"));
            sheet.createRow(r++).createCell(0).setCellValue("Rute: " + (bus.rute != null ? bus.rute.asal + " ➜ " + bus.rute.tujuan : "-"));
            sheet.createRow(r++).createCell(0).setCellValue("Keberangkatan: " + (bus.rute != null ? bus.rute.getFormattedDate() : "-"));

            // Summary
            long dewasa = dataList.stream().filter(p -> "DEWASA".equalsIgnoreCase(p.kategori_penumpang)).count();
            long anak = dataList.stream().filter(p -> "ANAK".equalsIgnoreCase(p.kategori_penumpang)).count();
            long bayi = dataList.stream().filter(p -> "BAYI".equalsIgnoreCase(p.kategori_penumpang)).count();

            r++;
            Row rHead = sheet.createRow(r++);
            String[] sumCols = {"Total", "Dewasa", "Anak", "Bayi"};
            for (int i = 0; i < sumCols.length; i++) { Cell c = rHead.createCell(i); c.setCellValue(sumCols[i]); c.setCellStyle(headerStyle); }
            Row rVal = sheet.createRow(r++);
            rVal.createCell(0).setCellValue(dataList.size());
            rVal.createCell(1).setCellValue(dewasa);
            rVal.createCell(2).setCellValue(anak);
            rVal.createCell(3).setCellValue(bayi);

            r++;
            // Data Penumpang
            Row tableHeader = sheet.createRow(r++);
            String[] cols = {"No", "Nama Peserta", "NIK", "Kategori", "JK", "Alamat", "No HP", "Kode Booking", "Status"};
            for (int i = 0; i < cols.length; i++) { Cell c = tableHeader.createCell(i); c.setCellValue(cols[i]); c.setCellStyle(headerStyle); }

            int no = 1;
            for (PendaftaranMudik p : dataList) {
                Row row = sheet.createRow(r++);
                row.createCell(0).setCellValue(no++);
                row.createCell(1).setCellValue(p.nama_peserta != null ? p.nama_peserta : "-");
                row.createCell(2).setCellValue(p.nik_peserta != null ? p.nik_peserta : "-");
                row.createCell(3).setCellValue(p.kategori_penumpang != null ? p.kategori_penumpang : "-");
                row.createCell(4).setCellValue(p.jenis_kelamin != null ? p.jenis_kelamin : "-");
                row.createCell(5).setCellValue(p.alamat_rumah != null ? p.alamat_rumah : "-");
                String hp = (p.no_hp_peserta != null && p.no_hp_peserta.length() > 5) ? p.no_hp_peserta : (p.user != null ? p.user.no_hp : "-");
                row.createCell(6).setCellValue(hp != null ? hp : "-");
                row.createCell(7).setCellValue(p.kode_booking != null ? p.kode_booking : "-");
                row.createCell(8).setCellValue(p.status_pendaftaran != null ? p.status_pendaftaran : "-");
            }

            for (int i = 0; i < cols.length; i++) sheet.autoSizeColumn(i);

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);
            return out.toByteArray();
        }
    }
}