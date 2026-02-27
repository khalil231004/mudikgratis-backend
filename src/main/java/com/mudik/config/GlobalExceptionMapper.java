package com.mudik.config;

import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import java.util.Arrays;
import java.util.List;

@Provider
public class GlobalExceptionMapper implements ExceptionMapper<Exception> {

    // Inject Headers buat baca dari mana request ini datang
    @Context
    HttpHeaders headers;

    private static final List<String> ALLOWED_ORIGINS = Arrays.asList(
            "https://seulamat.dishubaceh.com",
            "https://mudik.dishubaceh.com",
            "http://localhost:5173"
    );
    private static final String ALLOW_HDR = "origin,content-type,accept,authorization,userid,x-requested-with";
    private static final String ALLOW_MTD = "GET,POST,PUT,DELETE,OPTIONS,HEAD,PATCH";

    private String getValidOrigin() {
        if (headers != null) {
            String reqOrigin = headers.getHeaderString("Origin");
            if (reqOrigin != null && ALLOWED_ORIGINS.contains(reqOrigin)) {
                return reqOrigin;
            }
        }
        return "https://mudik.dishubaceh.com";
    }

    @Override
    public Response toResponse(Exception exception) {
        System.err.println("[GlobalExceptionMapper] Unhandled exception: " + exception.getClass().getName()
                + " — " + exception.getMessage());

        return Response.serverError()
                .header("Access-Control-Allow-Origin",      getValidOrigin())
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