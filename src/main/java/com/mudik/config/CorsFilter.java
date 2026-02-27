package com.mudik.config;

import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.container.PreMatching;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

//@Provider
@PreMatching
public class CorsFilter implements ContainerRequestFilter, ContainerResponseFilter {

    // Daftar domain yang diizinkan (Whitelist)
    private static final List<String> ALLOWED_ORIGINS = Arrays.asList(
            "https://seulamat.dishubaceh.com",
            "https://mudik.dishubaceh.com",
            "http://localhost:5173"
    );
    private static final String ALLOW_HDR = "origin,content-type,accept,authorization,userid,x-requested-with";
    private static final String ALLOW_MTD = "GET,POST,PUT,DELETE,OPTIONS,HEAD,PATCH";

    // Fungsi canggih buat ngecek dan mantulin origin yang valid
    private String getValidOrigin(ContainerRequestContext req) {
        String reqOrigin = req.getHeaderString("Origin");
        if (reqOrigin != null && ALLOWED_ORIGINS.contains(reqOrigin)) {
            return reqOrigin; // Pantulin balik domain yang request
        }
        return "https://mudik.dishubaceh.com"; // Default fallback
    }

    @Override
    public void filter(ContainerRequestContext req) throws IOException {
        if ("OPTIONS".equalsIgnoreCase(req.getMethod())) {
            req.abortWith(
                    Response.ok()
                            .header("Access-Control-Allow-Origin",      getValidOrigin(req))
                            .header("Access-Control-Allow-Headers",     ALLOW_HDR)
                            .header("Access-Control-Allow-Methods",     ALLOW_MTD)
                            .header("Access-Control-Allow-Credentials", "true")
                            .header("Access-Control-Max-Age",           "86400")
                            .build()
            );
        }
    }

    @Override
    public void filter(ContainerRequestContext req, ContainerResponseContext res) throws IOException {
        res.getHeaders().putSingle("Access-Control-Allow-Origin",      getValidOrigin(req));
        res.getHeaders().putSingle("Access-Control-Allow-Headers",     ALLOW_HDR);
        res.getHeaders().putSingle("Access-Control-Allow-Methods",     ALLOW_MTD);
        res.getHeaders().putSingle("Access-Control-Allow-Credentials", "true");
    }
}