package com.mudik.model;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "users")

public class User extends PanacheEntityBase {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long user_id;
    public String email;
    public String password_hash;
    public String nama_lengkap;
    public String nik; // NIK Kepala Keluarga
    public String status_akun; // AKTIF, BLOKIR
    public String verification_token; // Nyimpen kode rahasia UUID

    public LocalDateTime created_at;

    // Helper untuk cari user by Email
    public static User findByEmail(String email) {
        return find("email", email).firstResult();
    }
}