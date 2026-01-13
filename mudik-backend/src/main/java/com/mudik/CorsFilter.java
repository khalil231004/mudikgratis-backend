package com.mudik; // SESUAIKAN SAMA PACKAGE KAMU (misal: com.mudik)

import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.ext.Provider;
import java.io.IOException;

@Provider
public class CorsFilter implements ContainerResponseFilter {
    @Override
    public void filter(ContainerRequestContext requestContext,
                       ContainerResponseContext responseContext) throws IOException {

        // Ambil origin dari request secara dinamis (Trik biar tetap fleksibel tapi gak error)
        String origin = requestContext.getHeaderString("Origin");
        if (origin != null) {
            responseContext.getHeaders().add("Access-Control-Allow-Origin", origin);
        }

        responseContext.getHeaders().add("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS, HEAD");

        // Pastikan 'userId' ada di sini agar tidak kena 403 saat kirim header custom
        responseContext.getHeaders().add("Access-Control-Allow-Headers", "content-type, origin, accept, authorization, userId");

        // Izinkan credentials agar login tetap jalan
        responseContext.getHeaders().add("Access-Control-Allow-Credentials", "true");
    }
}