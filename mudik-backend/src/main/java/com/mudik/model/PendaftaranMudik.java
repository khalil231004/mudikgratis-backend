package com.mudik.model;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
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
    public User user;

    @ManyToOne
    public Rute rute;

    // --- DATA PESERTA ---

    @NotBlank(message = "Nama Peserta tidak boleh kosong")
    @Size(min = 3, message = "Nama minimal 3 huruf")
    public String nama_peserta;

    @Column(length = 16)
    @NotBlank(message = "NIK wajib diisi")
    @Pattern(regexp = "^[0-9]{16}$", message = "NIK harus 16 digit angka")
    public String nik_peserta;

    // REVISI: Validasi Regex dihapus dulu biar bisa NULL/Kosong
    // Nanti di Service kita cek: Kalau kosong -> Ambil No HP User Pemilik Akun
    public String no_hp_peserta;

    @NotBlank(message = "Jenis Kelamin wajib dipilih")
    public String jenis_kelamin;

    @NotNull(message = "Tanggal lahir wajib diisi")
    @Past(message = "Tanggal lahir tidak valid")
    public LocalDate tanggal_lahir;

    @NotBlank(message = "Kategori Penumpang wajib diisi")
    public String kategori_penumpang; // Dewasa, Anak, Lansia

    public String jenis_identitas; // KTP/KIA
    public String foto_identitas_path;

    // REVISI: Berat (Double) DIGANTI jadi Jenis (String)
    // Hapus public Double berat_barang;
    public String jenis_barang; // Contoh isi: "Koper Besar", "Tas Ransel", "Kardus"

    public String ukuran_barang; // Boleh dipertahankan atau dihapus jika dirasa duplikat

    public String status_pendaftaran;
    public String kode_booking;

    // REVISI: Sudah benar ada alamat rumah
    public String alamat_rumah;

    @CreationTimestamp
    public LocalDateTime created_at;
}