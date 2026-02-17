package com.mudik.config;

import jakarta.annotation.Priority;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;
import java.io.IOException;

@Provider
@Priority(Priorities.AUTHENTICATION - 100) // 🔥 JALAN PALING AWAL (Sebelum Auth)
public class CorsFilter implements ContainerRequestFilter, ContainerResponseFilter {

    @Override
    public void filter(ContainerRequestContext requestContext) throws IOException {
        // 🔥 1. CEGAT PREFLIGHT (OPTIONS)
        // Kalau browser cuma mau "nanya" (OPTIONS), langsung jawab OK 200.
        // Jangan biarkan lanjut ke pengecekan Token (karena OPTIONS gak bawa token).
        if (requestContext.getMethod().equalsIgnoreCase("OPTIONS")) {

            // Ambil Origin dari browser (contoh: https://seulamat.dishubaceh.com)
            String origin = requestContext.getHeaderString("Origin");
            if (origin == null) { origin = "*"; }

            requestContext.abortWith(Response.ok()
                    .header("Access-Control-Allow-Origin", origin)
                    .header("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS, HEAD")
                    // 👇 Daftar Header yang DIWAJIBKAN biar gak error "Request header field... not allowed"
                    .header("Access-Control-Allow-Headers", "origin, content-type, accept, authorization, userid, x-requested-with")
                    .header("Access-Control-Allow-Credentials", "true")
                    .header("Access-Control-Max-Age", "1209600")
                    .build());
        }
    }

    @Override
    public void filter(ContainerRequestContext requestContext, ContainerResponseContext responseContext) throws IOException {
        // 🔥 2. HANDLER REQUEST BIASA (Login, Profile, dll)
        // Kalau request berhasil lolos, kita tempel header CORS-nya lagi.
        if (!requestContext.getMethod().equalsIgnoreCase("OPTIONS")) {

            String origin = requestContext.getHeaderString("Origin");
            if (origin == null) { origin = "*"; }

            // Bersihkan header lama biar gak duplikat
            responseContext.getHeaders().remove("Access-Control-Allow-Origin");

            // Pasang Header Valid
            responseContext.getHeaders().add("Access-Control-Allow-Origin", origin);
            responseContext.getHeaders().add("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS, HEAD");
            responseContext.getHeaders().add("Access-Control-Allow-Headers", "origin, content-type, accept, authorization, userid, x-requested-with");
            responseContext.getHeaders().add("Access-Control-Allow-Credentials", "true");
        }
    }
}