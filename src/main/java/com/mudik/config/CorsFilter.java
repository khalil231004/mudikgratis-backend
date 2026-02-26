package com.mudik.config;

import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.container.PreMatching;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;
import java.io.IOException;
import java.util.Set;

/**
 * CorsFilter — backup filter untuk memastikan CORS header selalu ada,
 * termasuk pada response error (4xx/5xx) yang tidak dicover Quarkus built-in.
 *
 * @PreMatching memastikan OPTIONS preflight ditangkap SEBELUM auth check,
 * sehingga browser tidak kena 401 saat kirim preflight tanpa Authorization header.
 *
 * FIX: Tambah wildcard localhost untuk development, dan pastikan semua
 * error response (termasuk 401/403 dari JWT layer) tetap punya CORS header.
 */
@Provider
@PreMatching
public class CorsFilter implements ContainerRequestFilter, ContainerResponseFilter {

    // Daftar origin yang diizinkan (production + development)
    private static final Set<String> ALLOWED_ORIGINS = Set.of(
            "https://seulamat.dishubaceh.com",
            "https://dishubosrm.acehprov.go.id",
            // Dev origins — hapus di production jika perlu
            "http://localhost:3000",
            "http://localhost:5173",
            "http://localhost:8080"
    );

    private static final String ALLOW_HDR = "origin,content-type,accept,authorization,userid,x-requested-with";
    private static final String ALLOW_MTD = "GET,POST,PUT,DELETE,OPTIONS,HEAD,PATCH";
    private static final String DEFAULT_ORIGIN = "https://seulamat.dishubaceh.com";

    /**
     * Tangkap OPTIONS preflight SEBELUM auth layer.
     * Tanpa ini: browser kirim preflight → Quarkus auth layer tolak dengan 401 → CORS error.
     */
    @Override
    public void filter(ContainerRequestContext req) throws IOException {
        if ("OPTIONS".equalsIgnoreCase(req.getMethod())) {
            String origin = req.getHeaderString("Origin");
            String allowOrigin = resolveAllowOrigin(origin);
            req.abortWith(
                    Response.ok()
                            .header("Access-Control-Allow-Origin",      allowOrigin)
                            .header("Vary",                             "Origin")
                            .header("Access-Control-Allow-Headers",     ALLOW_HDR)
                            .header("Access-Control-Allow-Methods",     ALLOW_MTD)
                            .header("Access-Control-Allow-Credentials", "true")
                            .header("Access-Control-Max-Age",           "86400")
                            .build()
            );
        }
    }

    /**
     * Tambahkan CORS headers ke SEMUA response termasuk 4xx/5xx.
     * putSingle dipakai agar tidak duplikasi jika Quarkus built-in sudah menambahkan.
     *
     * PENTING: Jika response 401 tidak punya CORS header, browser tampilkan
     * "CORS error" padahal sebenarnya itu 401 auth error — ini membingungkan developer!
     */
    @Override
    public void filter(ContainerRequestContext req, ContainerResponseContext res) throws IOException {
        String origin = req.getHeaderString("Origin");
        String allowOrigin = resolveAllowOrigin(origin);
        res.getHeaders().putSingle("Access-Control-Allow-Origin",      allowOrigin);
        res.getHeaders().putSingle("Vary",                             "Origin");
        res.getHeaders().putSingle("Access-Control-Allow-Headers",     ALLOW_HDR);
        res.getHeaders().putSingle("Access-Control-Allow-Methods",     ALLOW_MTD);
        res.getHeaders().putSingle("Access-Control-Allow-Credentials", "true");
    }

    /**
     * Kembalikan origin yang dikirim browser jika ada di whitelist,
     * fallback ke origin utama jika tidak dikenal atau null.
     */
    private String resolveAllowOrigin(String requestOrigin) {
        if (requestOrigin != null && ALLOWED_ORIGINS.contains(requestOrigin)) {
            return requestOrigin;
        }
        return DEFAULT_ORIGIN;
    }
}
