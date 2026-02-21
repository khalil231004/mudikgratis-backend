package com.mudik.model;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Feedback — penilaian dan komentar dari pengguna terhadap program mudik gratis.
 *
 * Rating 1-5 bintang + komentar teks bebas.
 * Dikaitkan dengan User (opsional) — bisa anonim jika user_id null.
 */
@Entity
@Table(name = "feedback")
public class Feedback extends PanacheEntity {

    /** Rating 1–5 bintang */
    @Column(nullable = false)
    public Integer rating;

    /** Komentar teks bebas (opsional) */
    @Column(columnDefinition = "TEXT")
    public String komentar;

    /** Nama yang tampil di publik (bisa dari profil atau "Anonim") */
    @Column(length = 150)
    public String nama_pengirim;

    /** User yang mengirim feedback (null jika anonim) */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    public User user;

    /** Waktu pengiriman */
    public LocalDateTime dikirim_at = LocalDateTime.now();

    /** Apakah feedback sudah disetujui tampil di homepage */
    @Column(columnDefinition = "BOOLEAN DEFAULT FALSE")
    public Boolean disetujui = false;
}
