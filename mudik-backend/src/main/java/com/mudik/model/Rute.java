package com.mudik.model;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

@Entity
@Table(name = "rute")
public class Rute extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long rute_id;

    // --- FITUR BRUTAL: OPTIMISTIC LOCKING ---
    // Ini 'jimat' biar kalau ada yang rebutan kursi terakhir,
    // sistem gak error hitungannya. Wajib ada.
    @Version
    public int version;
    // ----------------------------------------

    public String asal;
    public String tujuan;

    public String nama_bus;      // Contoh: "Sempati Star"
    public String plat_nomor;    // Contoh: "BL 7777 AA"

    public LocalDateTime tanggal_keberangkatan;

    // Harga kita hapus karena GRATIS

    public Integer kuota_total;
    public Integer kuota_tersisa; // Logic: Awal dibuat, nilainya = kuota_total

    // Helper buat format tanggal cantik di JSON/PDF
    public String getFormattedDate() {
        if (tanggal_keberangkatan == null) return "Jadwal Belum Rilis";
        try {
            return DateTimeFormatter.ofPattern("dd MMMM yyyy HH:mm")
                    .withLocale(new Locale("id", "ID"))
                    .format(tanggal_keberangkatan);
        } catch (Exception e) {
            return tanggal_keberangkatan.toString();
        }
    }
}