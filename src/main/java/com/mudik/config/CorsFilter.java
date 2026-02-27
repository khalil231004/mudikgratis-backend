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
 * CorsFilter — backup filter untuk memastikan CORS header selalu ada.
 *
 * CATATAN: CORS utama ditangani oleh Quarkus built-in di application.properties
 * (quarkus.http.cors=true). Filter ini sebagai backup untuk edge case dan error responses.
 *
 * @PreMatching memastikan OPTIONS preflight ditangkap SEBELUM auth check,
 * sehingga browser tidak kena 401 saat kirim preflight tanpa Authorization header.
 *
 * PENTING: Filter ini juga menangkap error response (4xx/5xx) yang mungkin tidak
 * mendapat CORS header dari Quarkus built-in jika terjadi error sebelum routing selesai.
 */
@Provider
@PreMatching
public class CorsFilter implements ContainerRequestFilter, ContainerResponseFilter {

    private static final String ORIGIN    = "https://seulamat.dishubaceh.com,https://mudik.dishubaceh.com,http://localhost:5173";
    private static final String ALLOW_HDR = "origin,content-type,accept,authorization,userid,x-requested-with";
    private static final String ALLOW_MTD = "GET,POST,PUT,DELETE,OPTIONS,HEAD,PATCH";

    // Tangkap OPTIONS preflight sebelum auth layer
    @Override
    public void filter(ContainerRequestContext req) throws IOException {
        if ("OPTIONS".equalsIgnoreCase(req.getMethod())) {
            req.abortWith(
                    Response.ok()
                            .header("Access-Control-Allow-Origin",      ORIGIN)
                            .header("Access-Control-Allow-Headers",     ALLOW_HDR)
                            .header("Access-Control-Allow-Methods",     ALLOW_MTD)
                            .header("Access-Control-Allow-Credentials", "true")
                            .header("Access-Control-Max-Age",           "86400")
                            .build()
            );
        }
    }

    // Tambahkan CORS headers ke SEMUA response (termasuk 4xx/5xx)
    // putSingle dipakai agar tidak duplikasi header jika Quarkus built-in sudah menambahkannya
    @Override
    public void filter(ContainerRequestContext req, ContainerResponseContext res) throws IOException {
        // Selalu override untuk memastikan header ada, termasuk saat error
        res.getHeaders().putSingle("Access-Control-Allow-Origin",      ORIGIN);
        res.getHeaders().putSingle("Access-Control-Allow-Headers",     ALLOW_HDR);
        res.getHeaders().putSingle("Access-Control-Allow-Methods",     ALLOW_MTD);
        res.getHeaders().putSingle("Access-Control-Allow-Credentials", "true");
    }
}