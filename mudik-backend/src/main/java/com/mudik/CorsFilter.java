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

        // 1. GANTI IP SPESIFIK JADI BINTANG (*)
        // Artinya: Siapapun yg connect ke WiFi ini boleh akses (Dymas, HP, dll)
        responseContext.getHeaders().add(
                "Access-Control-Allow-Origin", "*");

        // 2. Izinkan Method Lengkap
        responseContext.getHeaders().add(
                "Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS, HEAD");

        // 3. TAMBAHKAN 'userId' dan 'Authorization' KE SINI (PENTING!)
        // Kalau gak ditambahin, header 'userId' yang dikirim Dymas bakal ditolak
        responseContext.getHeaders().add(
                "Access-Control-Allow-Headers", "content-type, origin, accept, authorization, userId");

        responseContext.getHeaders().add(
                "Access-Control-Allow-Credentials", "true");
    }
}