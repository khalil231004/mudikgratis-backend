package com.mudik.config;

import jakarta.annotation.Priority;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;
import java.io.IOException;

@Provider
@Priority(1) // 🔥 JALAN PALING DULUAN (SEBELUM AUTH)
public class CorsFilter implements ContainerRequestFilter, ContainerResponseFilter {

    @Override
    public void filter(ContainerRequestContext requestContext) throws IOException {
        // 🔥 BYPASS PREFLIGHT: Kalau browser cuma mau "Cek Ombak" (OPTIONS),
        // langsung stop disini, balikin OK, jangan sampe masuk ke Security Check.
        if (requestContext.getMethod().equalsIgnoreCase("OPTIONS")) {
            requestContext.abortWith(Response.ok().build());
        }
    }

    @Override
    public void filter(ContainerRequestContext requestContext, ContainerResponseContext responseContext) throws IOException {
        // Ambil Origin pengirim (contoh: https://seulamat.dishubaceh.com)
        String origin = requestContext.getHeaderString("Origin");
        if (origin == null) {
            origin = "*"; // Fallback kalau bukan dari browser
        }

        // 🔥 PAKSA HEADER INI MUNCUL DI SEMUA BALASAN
        responseContext.getHeaders().add("Access-Control-Allow-Origin", origin);
        responseContext.getHeaders().add("Access-Control-Allow-Credentials", "true");
        responseContext.getHeaders().add("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS, HEAD");

        // INI YANG BIKIN ERROR LU TADI "Request header field ... is not allowed"
        // Kita paksa bolehin semuanya sekarang:
        responseContext.getHeaders().add("Access-Control-Allow-Headers", "Origin, Content-Type, Accept, Authorization, userid, X-Requested-With");
    }
}