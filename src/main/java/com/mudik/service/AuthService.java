package com.mudik.service;

import com.mudik.model.User;
import io.quarkus.elytron.security.common.BcryptUtil;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

@ApplicationScoped
public class AuthService {

    /**
     * Registrasi user baru — tanpa field email, tanpa verifikasi email.
     * Status langsung AKTIF.
     */
    @Transactional
    public User registerUser(String nama, String password, String nik, String nohp, String jenisKelamin) throws Exception {

        // Validasi NIK Duplikat
        if (User.count("nik", nik) > 0) {
            throw new Exception("NIK sudah terdaftar. Silakan login atau gunakan NIK lain.");
        }

        User newUser = new User();
        newUser.nama_lengkap = nama;
        newUser.password_hash = BcryptUtil.bcryptHash(password);
        newUser.nik = nik;
        newUser.no_hp = nohp;
        newUser.jenis_kelamin = jenisKelamin;
        newUser.role = "USER";
        newUser.status_akun = "AKTIF";

        newUser.persist();
        return newUser;
    }

    /**
     * Login fleksibel: bisa pakai NIK (user biasa) ATAU email (admin).
     * Deteksi otomatis berdasarkan format input — jika mengandung '@' dianggap email.
     */
    public User loginUser(String identifier, String passwordInput) throws Exception {
        User user = null;

        // Jika mengandung '@' → cari by email (untuk admin)
        // Jika tidak → cari by NIK (untuk user biasa)
        if (identifier != null && identifier.contains("@")) {
            user = User.find("email", identifier).firstResult();
            if (user == null) {
                throw new Exception("Email tidak ditemukan.");
            }
        } else {
            user = User.find("nik", identifier).firstResult();
            if (user == null) {
                throw new Exception("NIK tidak ditemukan.");
            }
        }

        if (!BcryptUtil.matches(passwordInput, user.password_hash)) {
            throw new Exception("Password salah.");
        }

        return user;
    }
}
