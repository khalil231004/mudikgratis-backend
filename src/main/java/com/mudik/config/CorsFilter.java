package com.mudik.config;

import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.ext.Provider;
import java.io.IOException;

@Provider
public class CorsFilter implements ContainerResponseFilter {

    @Override
    public void filter(ContainerRequestContext requestContext, ContainerResponseContext responseContext) throws IOException {
        // 1. Ambil Origin dari Request (Contoh: https://seulamat.dishubaceh.com)
        String origin = requestContext.getHeaderString("Origin");

        // Kalau Origin kosong (misal dari Postman/Curl), kasih default * atau biarin null
        if (origin == null) {
            origin = "*";
        }

        // 2. Set Header CORS (Dinotis sesuai Origin pengirim)
        responseContext.getHeaders().add("Access-Control-Allow-Origin", origin);
        responseContext.getHeaders().add("Access-Control-Allow-Headers", "origin, content-type, accept, authorization, userid, x-requested-with");
        responseContext.getHeaders().add("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS, HEAD");
        responseContext.getHeaders().add("Access-Control-Allow-Credentials", "true"); // Ini wajib true karena lu pake token/login

        // 3. 🔥 PENYELAMAT: Kalau methodnya OPTIONS (Preflight), PAKSA jadi 200 OK
        if (requestContext.getMethod().equalsIgnoreCase("OPTIONS")) {
            responseContext.setStatus(200);
        }
    }
}