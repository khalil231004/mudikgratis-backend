//package com.mudik.resource;
//
//import jakarta.ws.rs.*;
//import jakarta.ws.rs.core.Response;
//import java.io.File;
//import java.io.IOException;
//import java.nio.file.Files;
//
//@Path("/uploads")
//public class FileResource {
//
//    private static final String UPLOAD_DIR = "./uploads/";
//
//
//    @GET
//    @Path("/{fileName}")
//    public Response getFile(@PathParam("fileName") String fileName) throws IOException {
//
//        File file = new File(UPLOAD_DIR + fileName);
//
//        if (!file.exists()) {
//            return Response.status(Response.Status.NOT_FOUND).build();
//        }
//
//        String contentType = Files.probeContentType(file.toPath());
//        if (contentType == null) {
//            contentType = "application/octet-stream";
//        }
//        return Response.ok(file)
//                .header("Content-Disposition", "inline; filename=\"" + file.getName() + "\"")
//                .type(contentType)
//                .build();
//    }
//}