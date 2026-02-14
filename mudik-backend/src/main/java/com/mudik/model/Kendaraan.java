package com.mudik.model;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties; // 🔥 WAJIB IMPORT INI

@Entity
@Table(name = "kendaraan")
public class Kendaraan extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    public String nama_armada;
    public String jenis_kendaraan;

    public String plat_nomor;
    public String nama_supir;
    public String kontak_supir;

    public Integer kapasitas_total;

    public Integer terisi;

    // 🔥 PERBAIKAN DI SINI (STOP LOOPING)
    // Kita ambil data Rute-nya, TAPI jangan ambil 'listKendaraan' yang ada di dalam Rute itu.
    // Biar gak muter-muter (Infinite Recursion).
    @ManyToOne
    @JoinColumn(name = "rute_id")
    @JsonIgnoreProperties("listKendaraan")
    public Rute rute;
}