package com.mudik.scheduler;

import com.mudik.model.PendaftaranMudik;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Scheduler: setiap 2 menit, cek pendaftar dengan status DITOLAK
 * yang sudah melewati batas perbaikan 1 jam.
 * Jika terlewat → otomatis ubah status ke DIBATALKAN.
 */
@ApplicationScoped
public class TolakAutoExpiredScheduler {

    @Scheduled(every = "2m") // jalankan setiap 2 menit
    @Transactional
    public void autoBatalkanYangExpired() {
        LocalDateTime batasWaktu = LocalDateTime.now().minusHours(1);

        // Cari semua pendaftar DITOLAK yang tolak_at-nya sudah > 1 jam lalu
        List<PendaftaranMudik> expired = PendaftaranMudik.list(
            "status_pendaftaran = 'DITOLAK' AND tolak_at IS NOT NULL AND tolak_at < ?1",
            batasWaktu
        );

        if (expired.isEmpty()) return;

        int count = 0;
        for (PendaftaranMudik p : expired) {
            try {
                // DITOLAK tidak pernah mengurangi kuota, jadi saat DIBATALKAN
                // dari DITOLAK, kita tidak perlu kembalikan kuota rute.
                // (kuota sudah dipegang oleh status PENDING anggota keluarga lain,
                //  atau tidak pernah berkurang sama sekali)

                // Batalkan kendaraan jika ada
                if (p.kendaraan != null) {
                    p.kendaraan.terisi = Math.max(0,
                        (p.kendaraan.terisi != null ? p.kendaraan.terisi : 0) - 1);
                    p.kendaraan.persist();
                    p.kendaraan = null;
                }

                p.status_pendaftaran = "DIBATALKAN";
                p.alasan_tolak = (p.alasan_tolak != null ? p.alasan_tolak : "")
                    + " [AUTO-BATAL: Batas perbaikan 1 jam terlewat]";
                p.tolak_at = null;
                p.link_konfirmasi_dikirim = false;
                p.persist();
                count++;

                System.out.println("[AUTO-BATAL] Peserta " + p.nama_peserta
                    + " (ID: " + p.pendaftaran_id + ") dibatalkan karena batas perbaikan terlewat.");
            } catch (Exception e) {
                System.err.println("[AUTO-BATAL] Gagal batalkan ID " + p.pendaftaran_id + ": " + e.getMessage());
            }
        }

        if (count > 0) {
            System.out.println("[AUTO-BATAL] Total " + count + " peserta dibatalkan otomatis.");
        }
    }
}