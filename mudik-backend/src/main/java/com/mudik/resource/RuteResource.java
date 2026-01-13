package com.mudik.resource;

import com.mudik.model.Rute;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.List;
import java.util.stream.Collectors;
import java.util.Map;
import java.util.HashMap;

@Path("/api/rute")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class RuteResource {

    @GET
    public Response getAllRute() {
        // Ambil data dari database
        List<Rute> listRute = Rute.listAll();
        List<Map<String, Object>> hasil = listRute.stream().map(r -> {
            Map<String, Object> map = new HashMap<>();
            map.put("route_id", r.route_id);
            map.put("asal", r.asal);
            map.put("tujuan", r.tujuan);

            return map;
        }).collect(Collectors.toList());

        return Response.ok(hasil).build();
    }
}