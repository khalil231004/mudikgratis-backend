package com.mudik.service;

import com.mudik.model.User;
import io.quarkus.elytron.security.common.BcryptUtil;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.transaction.Transactional;
import java.util.logging.Logger;

@ApplicationScoped
public class DatabaseInitializer {

    private static final Logger LOGGER = Logger.getLogger(DatabaseInitializer.class.getName());

    @Transactional
    public void onStart(@Observes StartupEvent ev) {
        String adminEmail = "admin@dishub.go.id";

        // 1. Cek apakah admin sudah ada di database?
        long count = User.count("email", adminEmail);

        if (count == 0) {
            LOGGER.info(">>> Inisialisasi Akun Admin Utama...");

            // 2. Buat objek User baru
            User admin = new User();
            admin.nama_lengkap = "Admin Utama Dishub";
            admin.email = adminEmail;

            // 🔥 Hash password menggunakan Bcrypt
            admin.password_hash = BcryptUtil.bcryptHash("admin123");

            admin.nik = "1171000000000001";
            admin.no_hp = "08116812345";
            admin.jenis_kelamin = "LAKI-LAKI";
            admin.role = "ADMIN"; // Wajib ADMIN agar bisa masuk portal admin
            admin.status_akun = "AKTIF"; // Langsung aktif tanpa verifikasi email

            // 3. Simpan ke database
            admin.persist();

            LOGGER.info("✅ Akun Admin Berhasil Dibuat: " + adminEmail);
        } else {
            LOGGER.info(">>> Akun Admin sudah tersedia, skip inisialisasi.");
        }
    }
}