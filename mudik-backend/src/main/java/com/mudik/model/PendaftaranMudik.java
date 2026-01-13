package com.mudik.model;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "pendaftaran_mudik")
public class PendaftaranMudik extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long pendaftaran_id;

    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    public User user;

    @ManyToOne
    @JoinColumn(name = "route_id", nullable = false)
    public Rute rute;

    public String nik_peserta;
    public String nama_peserta;
    public String hubungan_keluarga;
    public String foto_ktp_path;
    public String foto_kk_path;
    public String foto_barang_path;
    public String kode_token_barang;
    public String status_pendaftaran;
    public String bot_flag;
    public LocalDateTime created_at;
}