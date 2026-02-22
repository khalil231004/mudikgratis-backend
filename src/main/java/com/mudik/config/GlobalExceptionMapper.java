package com.mudik.config;

import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

/**
 * GlobalExceptionMapper — memastikan semua unhandled exception tetap mengembalikan
 * CORS header sehingga browser tidak melaporkan "No Access-Control-Allow-Origin".
 *
 * Tanpa ini, exception yang tidak ditangkap di dalam JAX-RS resource akan menghasilkan
 * response 500 TANPA CORS header → browser salah baca sebagai CORS error.
 */
@Provider
public class GlobalExceptionMapper implements ExceptionMapper<Exception> {

    private static final String ORIGIN    = "https://seulamat.dishubaceh.com";
    private static final String ALLOW_HDR = "origin,content-type,accept,authorization,userid,x-requested-with";
    private static final String ALLOW_MTD = "GET,POST,PUT,DELETE,OPTIONS,HEAD,PATCH";

    @Override
    public Response toResponse(Exception exception) {
        // Log ke stderr agar bisa dicek di server log
        System.err.println("[GlobalExceptionMapper] Unhandled exception: " + exception.getClass().getName()
                + " — " + exception.getMessage());

        return Response.serverError()
                .header("Access-Control-Allow-Origin",      ORIGIN)
                .header("Access-Control-Allow-Headers",     ALLOW_HDR)
                .header("Access-Control-Allow-Methods",     ALLOW_MTD)
                .header("Access-Control-Allow-Credentials", "true")
                .entity(java.util.Map.of(
                        "error",   "Internal Server Error",
                        "detail",  exception.getMessage() != null ? exception.getMessage() : "Unknown error",
                        "type",    exception.getClass().getSimpleName()
                ))
                .type("application/json")
                .build();
    }
}