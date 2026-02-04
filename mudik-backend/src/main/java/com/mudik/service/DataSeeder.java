package com.mudik.service;

import com.mudik.model.User;
import io.quarkus.runtime.StartupEvent;
import io.quarkus.elytron.security.common.BcryptUtil; // Pastikan import ini bener
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.transaction.Transactional;

@ApplicationScoped
public class DataSeeder {

    @Transactional
    public void onStart(@Observes StartupEvent ev) {
        // Cek dulu, udah ada admin belum?
        if (User.find("email", "admin@dishub.go.id").firstResult() == null) {

            User admin = new User();
            admin.nama_lengkap = "Super Admin Dishub";
            admin.email = "admin@dishub.go.id";

            // NAH INI KUNCINYA!
            // Kita hash passwordnya pake fungsi bawaan Quarkus biar pasti cocok.
            admin.password_hash = BcryptUtil.bcryptHash("admin123");

            admin.role = "ADMIN";

            admin.persist();

            System.out.println("✅ SUKSES: Akun Admin Dibuat Otomatis!");
            System.out.println("📧 Email: admin@dishub.go.id");
            System.out.println("🔑 Pass : admin123");
        }
    }
}