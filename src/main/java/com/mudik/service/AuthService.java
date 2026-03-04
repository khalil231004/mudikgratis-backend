package com.mudik.service;

import com.mudik.model.User;
import io.quarkus.elytron.security.common.BcryptUtil;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

@ApplicationScoped
public class AuthService {

    /**
     * Logika Registrasi dengan Validasi Manual
     * Menggunakan throws Exception agar pesan error spesifik ditangkap oleh AuthResource
     */
    @Transactional
    public User registerUser(String nama, String email, String password, String nik, String nohp, String jenisKelamin) throws Exception {

        // 1. Validasi NIK Duplikat (Kunci agar popup di Frontend muncul)
        if (User.count("nik", nik) > 0) {
            throw new Exception("NIK sudah terdaftar. Silakan login atau gunakan NIK lain.");
        }

        // 2. Validasi Email Duplikat
        if (User.count("email", email) > 0) {
            throw new Exception("Email sudah terdaftar. Gunakan email lain.");
        }

        // 3. Persiapan Data User Baru
        User newUser = new User();
        newUser.nama_lengkap = nama;
        newUser.email = email;

        // Pastikan field di model User bernama 'password' (sesuai AuthResource sebelumnya)
        // Jika di model kamu namanya 'password_hash', ganti baris di bawah ini
        newUser.password_hash = BcryptUtil.bcryptHash(password);

        newUser.nik = nik;
        newUser.no_hp = nohp;
        newUser.jenis_kelamin = jenisKelamin;

        newUser.role = "USER";
        newUser.status_akun = "BELUM_VERIF";

        // Simpan ke Database
        newUser.persist();

        return newUser;
    }

    /**
     * Logika Login
     */
    public User loginUser(String email, String passwordInput) throws Exception {
        User user = User.find("email", email).firstResult();

        if (user == null) {
            throw new Exception("Email tidak ditemukan.");
        }

        // Cek kecocokan password BCrypt
        if (!BcryptUtil.matches(passwordInput, user.password_hash)) {
            throw new Exception("Password salah.");
        }

        return user;
    }
}