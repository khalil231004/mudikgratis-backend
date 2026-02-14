package com.mudik.model;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import org.hibernate.annotations.CreationTimestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "pendaftaran_mudik")
public class PendaftaranMudik extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long pendaftaran_id;

    @ManyToOne
    public User user; // Akun Kepala Keluarga

    @ManyToOne
    public Rute rute;

    // 🔥 INI YANG BIKIN ERROR: Pastikan baris ini ada!
    // Relasi ke tabel Kendaraan
    @ManyToOne
    public Kendaraan kendaraan;

    public String nomor_kursi;

    // --- DATA PESERTA ---
    @NotBlank(message = "Nama peserta wajib diisi")
    public String nama_peserta;

    @Column(length = 16)
    @NotBlank(message = "NIK wajib 16 digit")
    public String nik_peserta;

    public String no_hp_peserta;
    public String jenis_kelamin;
    public LocalDate tanggal_lahir;
    public String kategori_penumpang; // Dewasa, Anak, Bayi

    // --- STATUS & VERIFIKASI ---
    public String status_pendaftaran; // MENUNGGU_VERIFIKASI, DITERIMA, DITOLAK

    // Fitur H-3 (Verifikasi Ulang)
    public boolean konfirmasi_h3;
    public LocalDateTime waktu_konfirmasi_h3;

    // Fitur Blacklist
    public boolean is_blacklisted;

    // Barang & Lainnya
    public String jenis_identitas;
    public String foto_identitas_path;
    public String jenis_barang;
    public String ukuran_barang;
    public String alamat_rumah;
    public String kode_booking;

    @CreationTimestamp
    public LocalDateTime created_at;
}