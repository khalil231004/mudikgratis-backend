package com.mudik.model;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.List;

@Entity
@Table(name = "rute")
public class Rute extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long rute_id;

    @Version
    public int version; // Optimistic Locking (Jimat biar gak crash pas rebutan)

    public String asal;
    public String tujuan;
    public LocalDateTime tanggal_keberangkatan;

    // --- TRIPLE KUOTA LOGIC (Sesuai Request) ---
    // 1. Total kursi dari semua bus yang didaftarkan ke rute ini
    public Integer kuota_total;

    // 2. Kursi yang sudah dibooking orang (Status: MENUNGGU / DITERIMA)
    public Integer kuota_terisi;

    // 3. Kursi yang sudah Fix Berangkat (Status: KONFIRMASI H-3)
    public Integer kuota_fix;

    // Helper: Hitung sisa kursi real-time untuk Frontend
    public int getSisaKuota() {
        if (kuota_total == null) kuota_total = 0;
        if (kuota_terisi == null) kuota_terisi = 0;
        return kuota_total - kuota_terisi;
    }

    // --- RELASI KE KENDARAAN ---
    // Satu Rute punya Banyak Kendaraan (Bus, HiAce, dll)
    @OneToMany(mappedBy = "rute", cascade = CascadeType.ALL)
    public List<Kendaraan> listKendaraan;

    // Helper Format Tanggal (JSON/PDF)
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