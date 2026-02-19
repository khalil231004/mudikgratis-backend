package com.mudik.config;

import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.container.PreMatching;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;
import java.io.IOException;

/**
 * CORS Filter — dua filter sekaligus:
 *
 * 1. ContainerRequestFilter (@PreMatching):
 *    Mencegat OPTIONS preflight SEBELUM auth check berjalan.
 *    Langsung return 200 + CORS headers agar browser puas.
 *    Tanpa ini, PUT/POST multipart dari frontend akan kena 401
 *    karena preflight tidak membawa Authorization header.
 *
 * 2. ContainerResponseFilter:
 *    Menambahkan CORS headers ke semua response normal (GET/POST/PUT/DELETE).
 */
@Provider
@PreMatching
public class CorsFilter implements ContainerRequestFilter, ContainerResponseFilter {

    private static final String ORIGIN      = "https://seulamat.dishubaceh.com";
    private static final String ALLOW_HDR   = "origin, content-type, accept, authorization, userid, x-requested-with";
    private static final String ALLOW_MTD   = "GET, POST, PUT, DELETE, OPTIONS, HEAD, PATCH";

    // ----------------------------------------------------------------
    // 1. REQUEST FILTER — tangkap OPTIONS sebelum auth
    // ----------------------------------------------------------------
    @Override
    public void filter(ContainerRequestContext req) throws IOException {
        if ("OPTIONS".equalsIgnoreCase(req.getMethod())) {
            req.abortWith(
                    Response.ok()
                            .header("Access-Control-Allow-Origin",      ORIGIN)
                            .header("Access-Control-Allow-Headers",     ALLOW_HDR)
                            .header("Access-Control-Allow-Methods",     ALLOW_MTD)
                            .header("Access-Control-Allow-Credentials", "true")
                            .header("Access-Control-Max-Age",           "86400") // cache preflight 1 hari
                            .build()
            );
        }
    }

    // ----------------------------------------------------------------
    // 2. RESPONSE FILTER — tambahkan CORS headers ke semua response
    // ----------------------------------------------------------------
    @Override
    public void filter(ContainerRequestContext req, ContainerResponseContext res) throws IOException {
        res.getHeaders().putSingle("Access-Control-Allow-Origin",      ORIGIN);
        res.getHeaders().putSingle("Access-Control-Allow-Headers",     ALLOW_HDR);
        res.getHeaders().putSingle("Access-Control-Allow-Methods",     ALLOW_MTD);
        res.getHeaders().putSingle("Access-Control-Allow-Credentials", "true");
    }
}