package com.mudik.resource;

import com.mudik.model.PendaftaranMudik;
import com.mudik.model.Rute;
import com.mudik.model.Terminal;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import java.util.ArrayList;
import java.util.List;

@Path("/api/peta")
public class PetaResource {


    public static class InfoRute {
        public String kota_asal;
        public Double asal_lat;
        public Double asal_lon;
        public String kota_tujuan;
        public Double tujuan_lat;
        public Double tujuan_lon;
        public Long jumlah_pemudik;
    }

    @GET
    @Path("/sebaran-pemudik")
    @Produces(MediaType.APPLICATION_JSON)
    public List<InfoRute> getSebaran() {
        List<InfoRute> hasilAkhir = new ArrayList<>();

        List<Rute> semuaRute = Rute.listAll();

        for (Rute r : semuaRute) {
            long jumlah = PendaftaranMudik.count(
                    "rute = ?1 AND (status_pendaftaran = 'APPROVED' OR status_pendaftaran = 'DITERIMA')",
                    r
            );
            if (jumlah > 0) {
                Terminal tTujuan = Terminal.find("nama = ?1 OR kota = ?1", r.tujuan).firstResult();
                Terminal tAsal = Terminal.find("nama = ?1 OR kota = ?1", r.asal).firstResult();


                boolean asalIsBandaAceh = (r.asal != null && r.asal.equalsIgnoreCase("Banda Aceh"));


                if (tTujuan != null && (tAsal != null || asalIsBandaAceh)) {
                    InfoRute info = new InfoRute();


                    if (asalIsBandaAceh) {
                        info.kota_asal = "Terminal Batoh";
                        info.asal_lat = 5.529939135839976;
                        info.asal_lon = 95.32882703823104;
                    } else {
                        info.kota_asal = r.asal;
                        info.asal_lat = tAsal.latitude;
                        info.asal_lon = tAsal.longitude;
                    }
                    info.kota_tujuan = r.tujuan;
                    info.tujuan_lat = tTujuan.latitude;
                    info.tujuan_lon = tTujuan.longitude;
                    info.jumlah_pemudik = jumlah;
                    hasilAkhir.add(info);
                }
            }
        }

        return hasilAkhir;
    }
}