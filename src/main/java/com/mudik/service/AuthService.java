package com.mudik.service;

import com.mudik.model.User;
import io.quarkus.elytron.security.common.BcryptUtil;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

@ApplicationScoped
public class AuthService {

    /**
     * Registrasi user baru — tanpa field email, tanpa verifikasi email.
     * Status langsung diset AKTIF oleh AuthResource setelah method ini.
     */
    @Transactional
    public User registerUser(String nama, String password, String nik, String nohp, String jenisKelamin) throws Exception {

        // 1. Validasi NIK Duplikat
        if (User.count("nik", nik) > 0) {
            throw new Exception("NIK sudah terdaftar. Silakan login atau gunakan NIK lain.");
        }

        // 2. Persiapan Data User Baru
        User newUser = new User();
        newUser.nama_lengkap = nama;
        newUser.password_hash = BcryptUtil.bcryptHash(password);
        newUser.nik = nik;
        newUser.no_hp = nohp;
        newUser.jenis_kelamin = jenisKelamin;
        newUser.role = "USER";
        newUser.status_akun = "AKTIF"; // Default AKTIF — tidak perlu verif

        // Simpan ke Database
        newUser.persist();

        return newUser;
    }

    /**
     * Login menggunakan NIK + password (bukan email).
     */
    public User loginUser(String nik, String passwordInput) throws Exception {
        User user = User.find("nik", nik).firstResult();

        if (user == null) {
            throw new Exception("NIK tidak ditemukan.");
        }

        // Cek kecocokan password BCrypt
        if (!BcryptUtil.matches(passwordInput, user.password_hash)) {
            throw new Exception("Password salah.");
        }

        return user;
    }
}
