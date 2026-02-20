package com.mudik.model;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * PortalConfig — konfigurasi global portal Mudik Gratis.
 *
 * Tabel ini selalu hanya punya 1 baris (id = 1).
 * Digunakan untuk mengontrol seluruh portal sistem secara terpusat dari Admin.
 *
 * Cakupan portal:
 *   1. portal_register_open  → membuka/menutup pendaftaran akun baru
 *   2. portal_mudik_open     → membuka/menutup pendaftaran mudik
 *   3. sesi_aktif            → apakah program mudik gratis masih berjalan
 *
 * Catatan: portal per-rute tetap dikontrol di model Rute (is_portal_open).
 */
@Entity
@Table(name = "portal_config")
public class PortalConfig extends PanacheEntityBase {

    @Id
    public Long id = 1L; // Singleton — selalu id = 1

    // ── PORTAL 1: REGISTER AKUN BARU ────────────────────────────────
    /** Jika false → endpoint /api/auth/register menolak pendaftaran baru */
    @Column(columnDefinition = "BOOLEAN DEFAULT TRUE")
    public Boolean portal_register_open = true;

    /** Pesan yang ditampilkan ke user saat portal register ditutup */
    @Column(length = 500)
    public String pesan_register_tutup = "Pendaftaran akun baru saat ini ditutup. Pantau informasi resmi di website Dishub Aceh.";

    // ── PORTAL 2: PENDAFTARAN MUDIK ──────────────────────────────────
    /** Jika false → endpoint /api/pendaftaran menolak pendaftaran mudik baru */
    @Column(columnDefinition = "BOOLEAN DEFAULT TRUE")
    public Boolean portal_mudik_open = true;

    /** Pesan yang ditampilkan ke user saat portal mudik ditutup */
    @Column(length = 500)
    public String pesan_mudik_tutup = "Pendaftaran Mudik Gratis sudah ditutup. Terima kasih atas antusias masyarakat Aceh!";

    // ── PORTAL 3: SESI MUDIK GRATIS ─────────────────────────────────
    /** Jika false → seluruh sistem menampilkan halaman 'Program Telah Berakhir' */
    @Column(columnDefinition = "BOOLEAN DEFAULT TRUE")
    public Boolean sesi_aktif = true;

    /** Pesan yang ditampilkan ke user saat sesi mudik berakhir */
    @Column(length = 500)
    public String pesan_sesi_berakhir = "Program Mudik Gratis Aceh 2026 telah selesai. Sampai jumpa di tahun berikutnya! 🚌";

    // ── METADATA ─────────────────────────────────────────────────────
    /** Waktu terakhir konfigurasi diubah oleh admin */
    public LocalDateTime updated_at;

    /** Siapa admin yang terakhir mengubah */
    @Column(length = 100)
    public String updated_by;

    // ── HELPER ───────────────────────────────────────────────────────
    /** Ambil konfigurasi tunggal, auto-buat jika belum ada */
    public static PortalConfig getInstance() {
        PortalConfig config = PortalConfig.findById(1L);
        if (config == null) {
            config = new PortalConfig();
            config.id = 1L;
            config.persist();
        }
        return config;
    }
}
