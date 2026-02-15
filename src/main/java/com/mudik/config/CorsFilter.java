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
        // Izinkan Domain Frontend lu (Bisa ganti * kalau mau brutal buat testing)
        responseContext.getHeaders().add("Access-Control-Allow-Origin", "*");

        // Izinkan Header Custom (userid) dan Header Standar (Authorization)
        responseContext.getHeaders().add("Access-Control-Allow-Headers", "origin, content-type, accept, authorization, userid, x-requested-with");

        // Izinkan Method HTTP
        responseContext.getHeaders().add("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS, HEAD");

        // Izinkan Credentials (kalau pake cookie/session)
        responseContext.getHeaders().add("Access-Control-Allow-Credentials", "true");

        // Jika ini adalah request OPTIONS (Preflight), langsung kasih status 200 OK
        if (requestContext.getMethod().equalsIgnoreCase("OPTIONS")) {
            responseContext.setStatus(200);
        }
    }
}