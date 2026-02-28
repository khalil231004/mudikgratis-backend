package com.mudik.scheduler;

import com.mudik.model.PendaftaranMudik;
import com.mudik.model.Rute;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.HashSet;

/**
 * Scheduler: setiap 2 menit, cek pendaftar DITOLAK yang sudah > 1 jam tidak diperbaiki.
 * Jika terlewat → SELURUH keluarga (DITOLAK + PENDING) dibatalkan, kuota rute dikembalikan.
 */
@ApplicationScoped
public class TolakAutoExpiredScheduler {

    @Scheduled(every = "2m")
    @Transactional
    public void autoBatalkanKeluargaYangExpired() {
        LocalDateTime batasWaktu = LocalDateTime.now().minusHours(1);

        // Cari semua pendaftar DITOLAK yang tolak_at sudah lewat 1 jam
        List<PendaftaranMudik> expiredDitolak = PendaftaranMudik.list(
                "status_pendaftaran = 'DITOLAK' AND tolak_at IS NOT NULL AND tolak_at < ?1",
                batasWaktu
        );

        if (expiredDitolak.isEmpty()) return;

        // Kumpulkan user_id yang punya anggota expired — tiap user diproses sekali
        Set<Long> userIdSudahDiproses = new HashSet<>();

        for (PendaftaranMudik expired : expiredDitolak) {
            if (expired.user == null) {
                // Tidak punya akun user — batalkan individu saja
                batalkanIndividu(expired, "[AUTO-BATAL: Batas perbaikan 1 jam terlewat]");
                continue;
            }

            Long userId = expired.user.user_id;
            if (userIdSudahDiproses.contains(userId)) continue;
            userIdSudahDiproses.add(userId);

            // Ambil SELURUH anggota keluarga
            List<PendaftaranMudik> semuaKeluarga = PendaftaranMudik.list(
                    "user.user_id = ?1", userId
            );

            System.out.println("[AUTO-BATAL] Proses keluarga userId=" + userId
                    + " (" + semuaKeluarga.size() + " anggota)");

            // Hitung berapa kuota yang perlu dikembalikan
            // (anggota PENDING = yang masih pegang kuota aktif)
            int kuotaDikembalikan = 0;

            for (PendaftaranMudik anggota : semuaKeluarga) {
                String status = anggota.status_pendaftaran;

                // Yang perlu dibatalkan: DITOLAK dan PENDING
                // (DIBATALKAN sudah selesai, DITERIMA H-3 / SIAP skip)
                if ("DITOLAK".equals(status) || "PENDING".equals(status)) {
                    // PENDING masih memegang slot kuota → kembalikan
                    if ("PENDING".equals(status)
                            && !"BAYI".equalsIgnoreCase(anggota.kategori_penumpang)) {
                        kuotaDikembalikan++;
                    }

                    // DITOLAK tidak pernah kurangi kuota, jadi tidak perlu dikembalikan

                    // Lepas plotting bus jika ada
                    if (anggota.kendaraan != null) {
                        anggota.kendaraan.terisi = Math.max(0,
                                (anggota.kendaraan.terisi != null ? anggota.kendaraan.terisi : 0) - 1);
                        anggota.kendaraan.persist();
                        anggota.kendaraan = null;
                    }

                    anggota.status_pendaftaran = "DIBATALKAN";
                    anggota.alasan_tolak = "[AUTO-BATAL: Batas perbaikan 1 jam terlewat, seluruh keluarga dibatalkan]";
                    anggota.tolak_at = null;
                    anggota.link_konfirmasi_dikirim = false;
                    anggota.persist();

                    System.out.println("[AUTO-BATAL]  → " + anggota.nama_peserta
                            + " (status lama: " + status + ") → DIBATALKAN");
                }
            }

            // Kembalikan kuota rute untuk anggota PENDING yang dibatalkan
            if (kuotaDikembalikan > 0 && !semuaKeluarga.isEmpty()
                    && semuaKeluarga.get(0).rute != null) {
                Rute rute = Rute.findById(semuaKeluarga.get(0).rute.rute_id);
                if (rute != null) {
                    rute.kuota_terisi = Math.max(0,
                            (rute.kuota_terisi != null ? rute.kuota_terisi : 0) - kuotaDikembalikan);
                    rute.persist();
                    System.out.println("[AUTO-BATAL]  → Kuota rute " + rute.tujuan
                            + " dikembalikan: " + kuotaDikembalikan + " slot");
                }
            }

            System.out.println("[AUTO-BATAL] Selesai proses userId=" + userId);
        }
    }

    private void batalkanIndividu(PendaftaranMudik p, String alasan) {
        if (p.kendaraan != null) {
            p.kendaraan.terisi = Math.max(0,
                    (p.kendaraan.terisi != null ? p.kendaraan.terisi : 0) - 1);
            p.kendaraan.persist();
            p.kendaraan = null;
        }
        p.status_pendaftaran = "DIBATALKAN";
        p.alasan_tolak = alasan;
        p.tolak_at = null;
        p.link_konfirmasi_dikirim = false;
        p.persist();
    }
}