package com.mudik.util;

import java.io.FileOutputStream;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;

public class GenerateTokenKey {
    public static void main(String[] args) throws Exception {
        // 1. Setup Generator (RSA 2048 bit)
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
        kpg.initialize(2048);
        KeyPair kp = kpg.generateKeyPair();

        // 2. Simpan Private Key (Buat Tanda Tangan)
        // Ini yg dipake Server buat bikin Token
        try (FileOutputStream out = new FileOutputStream("privateKey.pem")) {
            out.write("-----BEGIN PRIVATE KEY-----\n".getBytes());
            out.write(Base64.getMimeEncoder().encode(kp.getPrivate().getEncoded()));
            out.write("\n-----END PRIVATE KEY-----\n".getBytes());
        }

        // 3. Simpan Public Key (Buat Verifikasi/Cek)
        // Ini yg dipake Server buat ngecek Token asli/palsu
        try (FileOutputStream out = new FileOutputStream("publicKey.pem")) {
            out.write("-----BEGIN PUBLIC KEY-----\n".getBytes());
            out.write(Base64.getMimeEncoder().encode(kp.getPublic().getEncoded()));
            out.write("\n-----END PUBLIC KEY-----\n".getBytes());
        }

        System.out.println("✅ SUKSES! File privateKey.pem & publicKey.pem sudah jadi.");
        System.out.println("👉 Cek di folder paling luar proyekmu (sejajar sama pom.xml).");
    }
}