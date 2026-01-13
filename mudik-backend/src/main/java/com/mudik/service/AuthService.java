package com.mudik.service;

import com.mudik.model.User;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

import java.time.LocalDateTime;

@ApplicationScoped
public class AuthService {

    @Transactional
    public User registerUser(String nama, String email, String password, String nik, String nohp) {
        User cekEmail = User.find("email", email).firstResult();
        if (cekEmail != null) {
            throw new IllegalArgumentException("Email sudah terdaftar!");
        }
        User newUser = new User();
        newUser.nama_lengkap = nama;
        newUser.email = email;
        newUser.password_hash = password;
        newUser.nik = nik;
        newUser.no_hp = nohp;
        newUser.status_akun = "AKTIF";
        newUser.created_at = LocalDateTime.now();

        newUser.persist();

        return newUser;
    }

    public User loginUser(String email, String password) {
        User user = User.find("email", email).firstResult();

        if (user == null) {
            throw new IllegalArgumentException("Email tidak ditemukan!");
        }
        if (!user.password_hash.equals(password)) {
            throw new IllegalArgumentException("Password salah!");
        }
        return user;
    }
}