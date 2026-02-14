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

    @Column(unique = true, nullable = false)
    public String email;
    public String password_hash;
    public String nama_lengkap;
    public String nik;
    public String no_hp;
    public String role;
    public String jenis_kelamin;
    public String status_akun;
    public String verification_token;
    public LocalDateTime created_at;
    public String reset_token;
    public LocalDateTime token_expired_at;
}