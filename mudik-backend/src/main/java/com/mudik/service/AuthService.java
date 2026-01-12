package com.mudik.service;

import com.mudik.model.User;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

import java.time.LocalDateTime;

@ApplicationScoped
public class AuthService {

    // --- LOGIC REGISTER ---
    @Transactional
    public User registerUser(String nama, String email, String password, String nik, String hp) {

        // 1. Cek apakah email sudah ada?
        User cekEmail = User.find("email", email).firstResult();
        if (cekEmail != null) {
            throw new IllegalArgumentException("Email sudah terdaftar!");
        }

        // 2. Simpan User Baru
        User newUser = new User();
        newUser.nama_lengkap = nama;
        newUser.email = email;
        newUser.password_hash = password; // Nanti kita enkripsi kalau sempat, sekarang plain dulu
        newUser.nik = nik;
        newUser.status_akun = "AKTIF";
        newUser.created_at = LocalDateTime.now();

        newUser.persist();

        return newUser;
    }

    // --- LOGIC LOGIN ---
    public User loginUser(String email, String password) {
        // 1. Cari user berdasarkan email
        User user = User.find("email", email).firstResult();

        if (user == null) {
            throw new IllegalArgumentException("Email tidak ditemukan!");
        }

        // 2. Cek Password (Manual Check)
        if (!user.password_hash.equals(password)) {
            throw new IllegalArgumentException("Password salah!");
        }

        return user; // Login Sukses, balikin data user
    }
}