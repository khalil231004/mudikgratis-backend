package com.mudik.service;

import com.mudik.model.User;
import io.quarkus.elytron.security.common.BcryptUtil; // <--- WAJIB IMPORT INI
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import java.time.LocalDateTime;

@ApplicationScoped
public class AuthService {

    @Transactional
    public User registerUser(String nama, String email, String password, String nik, String nohp, String jenisKelamin) {

        if (User.find("email", email).firstResult() != null) {
            throw new IllegalArgumentException("Email sudah terdaftar!");
        }

        if (User.find("nik", nik).firstResult() != null) {
            throw new IllegalArgumentException("NIK sudah digunakan!");
        }

        User newUser = new User();
        newUser.nama_lengkap = nama;
        newUser.email = email;
        newUser.password_hash = BcryptUtil.bcryptHash(password);

        newUser.nik = nik;
        newUser.no_hp = nohp;
        newUser.jenis_kelamin = jenisKelamin;

        newUser.role = "USER";
        newUser.status_akun = "BELUM_VERIF";
        newUser.persist();
        return newUser;
    }

    public User loginUser(String email, String passwordInput) {
        User user = User.find("email", email).firstResult();

        if (user == null) {
            throw new IllegalArgumentException("Email atau Password salah!");
        }
        if (!BcryptUtil.matches(passwordInput, user.password_hash)) {
            throw new IllegalArgumentException("Email atau Password salah!");
        }

        return user;
    }
}