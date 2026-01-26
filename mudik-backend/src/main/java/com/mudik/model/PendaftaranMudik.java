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

    // --- BAGIAN INI JANGAN DIHAPUS TOTAL, CUKUP SEDERHANAKAN ---
    // Hapus @JoinColumn dan @NotNull biar IDE gak rewel.
    // Cukup @ManyToOne, nanti Hibernate otomatis bikin kolom 'user_user_id' atau 'user_id'.

    @ManyToOne
    public User user;

    @ManyToOne
    public Rute rute;
    // -----------------------------------------------------------

    @NotBlank(message = "Nama Peserta tidak boleh kosong")
    @Size(min = 3, message = "Nama minimal 3 huruf")
    public String nama_peserta;

    @Column(length = 16)
    @NotBlank(message = "NIK wajib diisi")
    @Pattern(regexp = "^[0-9]{16}$", message = "NIK harus 16 digit angka")
    public String nik_peserta;

    // HP Boleh Kosong (Sesuai request tadi)
    @Pattern(regexp = "^08[0-9]{8,13}$", message = "Format HP salah (08...)")
    public String no_hp_peserta;

    @NotBlank(message = "Jenis Kelamin wajib dipilih")
    public String jenis_kelamin;

    @NotNull(message = "Tanggal lahir wajib diisi")
    @Past(message = "Tanggal lahir tidak valid")
    public LocalDate tanggal_lahir;

    @NotBlank(message = "Kategori Penumpang wajib diisi")
    public String kategori_penumpang;

    public String jenis_identitas;
    public String foto_identitas_path;

    // Hapus @PositiveOrZero kalau bikin ribet, pake Double biasa aja
    public Double berat_barang;

    public String ukuran_barang;
    public String status_pendaftaran;
    public String kode_booking;
    public String titik_jemput;

    @CreationTimestamp
    public LocalDateTime created_at;
}