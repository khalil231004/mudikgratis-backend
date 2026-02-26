package com.mudik.config;

import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

import java.util.Map;
import java.util.Set;

/**
 * GlobalExceptionMapper — memastikan semua unhandled exception tetap
 * mengembalikan CORS header sehingga browser tidak melaporkan
 * "No Access-Control-Allow-Origin" saat terjadi error 500.
 *
 * FIX: Dukung dua origin (seulamat + dishubosrm), tidak hardcode satu saja.
 */
@Provider
public class GlobalExceptionMapper implements ExceptionMapper<Exception> {

    private static final Set<String> ALLOWED_ORIGINS = Set.of(
            "https://seulamat.dishubaceh.com",
            "https://dishubosrm.acehprov.go.id"
    );

    private static final String DEFAULT_ORIGIN = "https://seulamat.dishubaceh.com";
    private static final String ALLOW_HDR = "origin,content-type,accept,authorization,userid,x-requested-with";
    private static final String ALLOW_MTD = "GET,POST,PUT,DELETE,OPTIONS,HEAD,PATCH";

    // Inject HTTP headers untuk ambil Origin dari request
    @Context
    HttpHeaders httpHeaders;

    @Override
    public Response toResponse(Exception exception) {
        System.err.println("[GlobalExceptionMapper] Unhandled exception: "
                + exception.getClass().getName() + " — " + exception.getMessage());

        // Ambil origin dari request header (bisa null jika bukan browser request)
        String requestOrigin = null;
        try {
            if (httpHeaders != null) {
                requestOrigin = httpHeaders.getHeaderString("Origin");
            }
        } catch (Exception ignored) {}

        String allowOrigin = (requestOrigin != null && ALLOWED_ORIGINS.contains(requestOrigin))
                ? requestOrigin
                : DEFAULT_ORIGIN;

        return Response.serverError()
                .header("Access-Control-Allow-Origin",      allowOrigin)
                .header("Vary",                             "Origin")
                .header("Access-Control-Allow-Headers",     ALLOW_HDR)
                .header("Access-Control-Allow-Methods",     ALLOW_MTD)
                .header("Access-Control-Allow-Credentials", "true")
                .entity(Map.of(
                        "error",  "Internal Server Error",
                        "detail", exception.getMessage() != null ? exception.getMessage() : "Unknown error",
                        "type",   exception.getClass().getSimpleName()
                ))
                .type("application/json")
                .build();
    }
}