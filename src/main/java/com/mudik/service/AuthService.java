package com.mudik.service;

import com.mudik.model.User;
import io.quarkus.elytron.security.common.BcryptUtil;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

@ApplicationScoped
public class AuthService {

    /**
     * Registrasi langsung tanpa verifikasi email.
     * Status akun langsung AKTIF setelah daftar.
     */
    @Transactional
    public User registerUser(String nama, String password, String nik, String nohp, String jenisKelamin) throws Exception {

        // Validasi NIK Duplikat
        if (User.count("nik", nik) > 0) {
            throw new Exception("NIK sudah terdaftar. Silakan login atau gunakan NIK lain.");
        }

        // Persiapan Data User Baru
        User newUser = new User();
        newUser.nama_lengkap = nama;
        newUser.email = null; // Email tidak digunakan
        newUser.password_hash = BcryptUtil.bcryptHash(password);
        newUser.nik = nik;
        newUser.no_hp = nohp;
        newUser.jenis_kelamin = jenisKelamin;
        newUser.role = "USER";
        newUser.status_akun = "AKTIF"; // Langsung aktif tanpa verifikasi email

        newUser.persist();

        return newUser;
    }

    /**
     * Login menggunakan NIK (bukan email).
     */
    public User loginUser(String nik, String passwordInput) throws Exception {
        User user = User.find("nik", nik).firstResult();

        if (user == null) {
            throw new Exception("NIK tidak ditemukan.");
        }

        if (!BcryptUtil.matches(passwordInput, user.password_hash)) {
            throw new Exception("Password salah.");
        }

        if ("BANNED".equalsIgnoreCase(user.status_akun)) {
            throw new Exception("Akun Anda telah diblokir. Hubungi admin.");
        }

        return user;
    }
}