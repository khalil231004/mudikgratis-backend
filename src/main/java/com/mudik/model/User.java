package com.mudik.model;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import com.fasterxml.jackson.annotation.JsonIgnore; // 🔥 WAJIB IMPORT INI
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "users")
public class User extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long user_id;

    @Column(unique = true, nullable = false)
    public String email;

    // 🔥 SECURITY: Wajib di-ignore biar gak bocor ke JSON Frontend
    @JsonIgnore
    public String password_hash;

    public String nama_lengkap;
    public String nik;
    public String no_hp;

    // Default Role biasanya "USER"
    @Column(columnDefinition = "VARCHAR(20) DEFAULT 'USER'")
    public String role;

    public String jenis_kelamin;

    // Status: BELUM_VERIF, AKTIF, BANNED
    public String status_akun;

    // 🔥 Sembunyikan token verifikasi biar gak bisa diintip orang lain
    @JsonIgnore
    public String verification_token;

    @CreationTimestamp // 🔥 Otomatis isi tanggal saat data dibuat
    @Column(updatable = false)
    public LocalDateTime created_at;

    // 🔥 Sembunyikan data reset password
    @JsonIgnore
    public String reset_token;

    @JsonIgnore
    public LocalDateTime token_expired_at;

    // --- HELPER METHODS (Opsional tapi Guna) ---

    // Cari user berdasarkan email dengan cepat
    public static User findByEmail(String email) {
        return find("email", email).firstResult();
    }
}