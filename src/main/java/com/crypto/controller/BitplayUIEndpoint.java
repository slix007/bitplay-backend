package com.crypto.controller;

import com.crypto.model.VisualTrade;
import com.crypto.service.BitplayUIService;

import org.springframework.stereotype.Component;

import java.util.List;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;

/**
 * Created by Sergey Shurmin on 4/3/17.
 */
@Component
@Path("/market")
public class BitplayUIEndpoint {

    private final BitplayUIService service;

    public BitplayUIEndpoint(BitplayUIService service) {
        this.service = service;
    }


    @GET
    @Path("/")
    public String message() {
        return "Hello";
    }

    @GET
    @Path("/order-book")
    @Produces("application/json")
    public List<VisualTrade> messageOne() {
        return this.service.fetchTrades();
    }

}

