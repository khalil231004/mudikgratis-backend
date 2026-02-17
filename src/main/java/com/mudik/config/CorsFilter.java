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
@Priority(1) // 🔥 WAJIB: Angka 1 artinya filter ini jalan DULUAN sebelum Auth/Security
public class CorsFilter implements ContainerRequestFilter, ContainerResponseFilter {

    @Override
    public void filter(ContainerRequestContext requestContext) throws IOException {
        // 🔥 INTERCEPTOR: Kalau browser kirim "OPTIONS" (Cek Ombak)
        if (requestContext.getMethod().equalsIgnoreCase("OPTIONS")) {

            // Langsung hentikan proses (abort), jangan sampai masuk ke Security Check
            // Paksa balikin status 200 OK dengan header lengkap
            requestContext.abortWith(Response.ok()
                    .header("Access-Control-Allow-Origin", requestContext.getHeaderString("Origin") != null ? requestContext.getHeaderString("Origin") : "*")
                    .header("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS, HEAD")
                    .header("Access-Control-Allow-Headers", "origin, content-type, accept, authorization, userid, x-requested-with")
                    .header("Access-Control-Allow-Credentials", "true")
                    .header("Access-Control-Max-Age", "1209600")
                    .build());
        }
    }

    @Override
    public void filter(ContainerRequestContext requestContext, ContainerResponseContext responseContext) throws IOException {
        // Ini untuk request normal (Login, Update Profil, dll) yang lolos Security
        // Kita tempel lagi headernya biar browser senang
        if (!requestContext.getMethod().equalsIgnoreCase("OPTIONS")) {
            String origin = requestContext.getHeaderString("Origin");
            if (origin == null) {
                origin = "*";
            }

            // Hapus header lama kalau ada (biar gak duplikat)
            responseContext.getHeaders().remove("Access-Control-Allow-Origin");
            responseContext.getHeaders().remove("Access-Control-Allow-Credentials");
            responseContext.getHeaders().remove("Access-Control-Allow-Methods");
            responseContext.getHeaders().remove("Access-Control-Allow-Headers");

            // Pasang header baru
            responseContext.getHeaders().add("Access-Control-Allow-Origin", origin);
            responseContext.getHeaders().add("Access-Control-Allow-Credentials", "true");
            responseContext.getHeaders().add("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS, HEAD");
            responseContext.getHeaders().add("Access-Control-Allow-Headers", "origin, content-type, accept, authorization, userid, x-requested-with");
        }
    }
}