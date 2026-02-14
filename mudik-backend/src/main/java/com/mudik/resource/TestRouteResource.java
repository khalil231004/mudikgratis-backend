package com.mudik.resource;

import com.mudik.model.Terminal;
import com.mudik.client.OsrmClient;
import io.smallrye.mutiny.Uni;
import io.smallrye.common.annotation.Blocking; // <--- 1. WAJIB IMPORT INI
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.rest.client.inject.RestClient;

@Path("/api/test-rute")
public class TestRouteResource {

    @Inject
    @RestClient
    OsrmClient osrmClient;

    @GET
    @Path("/{idAsal}/{idTujuan}")
    @Blocking
    public Uni<Response> testHitungRute(@PathParam("idAsal") Long idAsal, @PathParam("idTujuan") Long idTujuan) {

        Terminal asal = Terminal.findById(idAsal);
        Terminal tujuan = Terminal.findById(idTujuan);

        if (asal == null || tujuan == null) {
            return Uni.createFrom().item(Response.status(404).entity("Terminal tidak ditemukan!").build());
        }
        String coordinates = asal.longitude + "," + asal.latitude + ";" +
                tujuan.longitude + "," + tujuan.latitude;

        return osrmClient.getRoute(coordinates, "full", "geojson")
                .onItem().transform(json -> Response.ok(json).build());
    }
}