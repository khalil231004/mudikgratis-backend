package com.mudik.resource;

import com.mudik.model.PortalConfig;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * PortalResource — kontrol global portal Mudik Gratis.
 *
 * Endpoint publik (dibaca frontend tanpa token):
 *   GET  /api/portal/status          → status semua portal (register, mudik, sesi)
 *
 * Endpoint admin (wajib ada token admin):
 *   PUT  /api/portal/register        → buka/tutup portal register akun
 *   PUT  /api/portal/mudik           → buka/tutup portal daftar mudik
 *   PUT  /api/portal/sesi            → buka/tutup sesi program mudik
 *   PUT  /api/portal/semua           → update semua sekaligus
 */
@Path("/api/portal")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class PortalResource {

    // ================================================================
    // PUBLIC — dibaca frontend tanpa token
    // ================================================================

    /**
     * GET /api/portal/status
     * Dipakai frontend untuk cek apakah portal aktif sebelum render halaman.
     * Response cepat & aman ditampilkan publik.
     */
    @GET
    @Path("/status")
    public Response getStatus() {
        PortalConfig cfg = PortalConfig.getInstance();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("sesi_aktif",            cfg.sesi_aktif);
        result.put("portal_register_open",  cfg.portal_register_open);
        result.put("portal_mudik_open",     cfg.portal_mudik_open);
        result.put("pesan_sesi_berakhir",   cfg.pesan_sesi_berakhir);
        result.put("pesan_register_tutup",  cfg.pesan_register_tutup);
        result.put("pesan_mudik_tutup",     cfg.pesan_mudik_tutup);
        result.put("updated_at",            cfg.updated_at);
        result.put("updated_by",            cfg.updated_by);

        return Response.ok(result).build();
    }

    // ================================================================
    // ADMIN — update individual portal
    // ================================================================

    /**
     * PUT /api/portal/register
     * Body: { "open": true/false, "pesan": "...", "admin": "nama admin" }
     */
    @PUT
    @Path("/register")
    @Transactional
    public Response toggleRegister(Map<String, Object> body) {
        return updatePortal(body, "register");
    }

    /**
     * PUT /api/portal/mudik
     * Body: { "open": true/false, "pesan": "...", "admin": "nama admin" }
     */
    @PUT
    @Path("/mudik")
    @Transactional
    public Response toggleMudik(Map<String, Object> body) {
        return updatePortal(body, "mudik");
    }

    /**
     * PUT /api/portal/sesi
     * Body: { "open": true/false, "pesan": "...", "admin": "nama admin" }
     */
    @PUT
    @Path("/sesi")
    @Transactional
    public Response toggleSesi(Map<String, Object> body) {
        return updatePortal(body, "sesi");
    }

    /**
     * PUT /api/portal/semua
     * Update semua portal sekaligus.
     * Body: {
     *   "sesi_aktif": true,
     *   "portal_register_open": true,
     *   "portal_mudik_open": false,
     *   "pesan_register_tutup": "...",
     *   "pesan_mudik_tutup": "...",
     *   "pesan_sesi_berakhir": "...",
     *   "admin": "nama admin"
     * }
     */
    @PUT
    @Path("/semua")
    @Transactional
    public Response updateSemua(Map<String, Object> body) {
        try {
            PortalConfig cfg = PortalConfig.getInstance();

            if (body.containsKey("sesi_aktif"))
                cfg.sesi_aktif = Boolean.parseBoolean(body.get("sesi_aktif").toString());

            if (body.containsKey("portal_register_open"))
                cfg.portal_register_open = Boolean.parseBoolean(body.get("portal_register_open").toString());

            if (body.containsKey("portal_mudik_open"))
                cfg.portal_mudik_open = Boolean.parseBoolean(body.get("portal_mudik_open").toString());

            if (body.containsKey("pesan_register_tutup") && body.get("pesan_register_tutup") != null)
                cfg.pesan_register_tutup = body.get("pesan_register_tutup").toString();

            if (body.containsKey("pesan_mudik_tutup") && body.get("pesan_mudik_tutup") != null)
                cfg.pesan_mudik_tutup = body.get("pesan_mudik_tutup").toString();

            if (body.containsKey("pesan_sesi_berakhir") && body.get("pesan_sesi_berakhir") != null)
                cfg.pesan_sesi_berakhir = body.get("pesan_sesi_berakhir").toString();

            cfg.updated_at = LocalDateTime.now();
            cfg.updated_by = body.getOrDefault("admin", "Admin").toString();
            cfg.persist();

            return Response.ok(Map.of(
                "status", "BERHASIL",
                "pesan", "Semua konfigurasi portal berhasil diperbarui"
            )).build();

        } catch (Exception e) {
            return Response.status(400).entity(Map.of("error", e.getMessage())).build();
        }
    }

    // ================================================================
    // INTERNAL HELPER
    // ================================================================
    private Response updatePortal(Map<String, Object> body, String type) {
        try {
            if (!body.containsKey("open"))
                return Response.status(400).entity(Map.of("error", "Field 'open' wajib diisi (true/false)")).build();

            boolean open = Boolean.parseBoolean(body.get("open").toString());
            String admin = body.getOrDefault("admin", "Admin").toString();
            String pesan = body.containsKey("pesan") ? body.get("pesan").toString() : null;

            PortalConfig cfg = PortalConfig.getInstance();

            String label;
            switch (type) {
                case "register" -> {
                    cfg.portal_register_open = open;
                    if (pesan != null) cfg.pesan_register_tutup = pesan;
                    label = "Portal Pendaftaran Akun";
                }
                case "mudik" -> {
                    cfg.portal_mudik_open = open;
                    if (pesan != null) cfg.pesan_mudik_tutup = pesan;
                    label = "Portal Pendaftaran Mudik";
                }
                case "sesi" -> {
                    cfg.sesi_aktif = open;
                    if (pesan != null) cfg.pesan_sesi_berakhir = pesan;
                    label = "Sesi Program Mudik Gratis";
                }
                default -> {
                    return Response.status(400).entity(Map.of("error", "Tipe portal tidak valid")).build();
                }
            }

            cfg.updated_at = LocalDateTime.now();
            cfg.updated_by = admin;
            cfg.persist();

            String kondisi = open ? "DIBUKA ✅" : "DITUTUP 🔒";
            return Response.ok(Map.of(
                "status", "BERHASIL",
                "pesan", label + " berhasil " + kondisi,
                "open", open
            )).build();

        } catch (Exception e) {
            return Response.status(400).entity(Map.of("error", e.getMessage())).build();
        }
    }
}
