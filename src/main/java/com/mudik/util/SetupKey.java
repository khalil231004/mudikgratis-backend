package com.mudik.util;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;

public class SetupKey {
    public static void main(String[] args) throws Exception {
        // 1. Tentukan Lokasi Folder Resources
        // Kita paksa tulis ke src/main/resources biar permanen
        String path = "src/main/resources/";
        File directory = new File(path);
        if (!directory.exists()) {
            System.out.println("❌ Folder tidak ketemu! Pastikan jalankan dari root project.");
            return;
        }

        // 2. Generate Kunci RSA 2048
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
        kpg.initialize(2048);
        KeyPair kp = kpg.generateKeyPair();

        // 3. Tulis Private Key (PKCS#8) - HATI-HATI JANGAN DIUBAH
        try (FileOutputStream out = new FileOutputStream(path + "privateKey.pem")) {
            out.write("-----BEGIN PRIVATE KEY-----\n".getBytes());
            // MimeEncoder nambahin enter otomatis setiap 76 karakter (PENTING!)
            out.write(Base64.getMimeEncoder().encode(kp.getPrivate().getEncoded()));
            out.write("\n-----END PRIVATE KEY-----\n".getBytes());
        }

        // 4. Tulis Public Key (X.509)
        try (FileOutputStream out = new FileOutputStream(path + "publicKey.pem")) {
            out.write("-----BEGIN PUBLIC KEY-----\n".getBytes());
            out.write(Base64.getMimeEncoder().encode(kp.getPublic().getEncoded()));
            out.write("\n-----END PUBLIC KEY-----\n".getBytes());
        }

        System.out.println("✅ BERHASIL! File privateKey.pem & publicKey.pem sudah dibuat ulang dengan rapi.");
        System.out.println("👉 Lokasi: " + Paths.get(path).toAbsolutePath());
    }
}