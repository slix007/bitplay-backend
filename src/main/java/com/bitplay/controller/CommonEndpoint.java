package com.bitplay.controller;

import com.bitplay.model.BorderUpdateJson;
import com.bitplay.model.DeltasJson;
import com.bitplay.model.MakerDeltalUpdateJson;
import com.bitplay.model.TradeLogJson;
import com.bitplay.service.CommonUIService;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

/**
 * Created by Sergey Shurmin on 4/15/17.
 */
@Component
@Path("/")
public class CommonEndpoint {

    @Autowired
    private CommonUIService commonUIService;

    @GET
    @Path("/")
    public String help() {
        return "Use /market/{marketName}";
    }

    @GET
    @Path("/market")
    public String message() {
        return "Hello. Use /market/{marketName}/{operationName}";
    }

    @GET
    @Path("/market/trade-log/poloniex")
    @Produces(MediaType.APPLICATION_JSON)
    public TradeLogJson getPoloniexTradeLog() {
        return commonUIService.getPoloniexTradeLog();
    }

    @GET
    @Path("/market/trade-log/okcoin")
    @Produces(MediaType.APPLICATION_JSON)
    public TradeLogJson getOkcoinTradeLog() {
        return commonUIService.getOkCoinTradeLog();
    }

    @GET
    @Path("/market/deltas")
    @Produces(MediaType.APPLICATION_JSON)
    public DeltasJson deltas() {
        return commonUIService.getDeltas();
    }

    @POST
    @Path("/market/update-borders")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public DeltasJson updateBorders(BorderUpdateJson borderUpdateJson) {
        return commonUIService.updateBorders(borderUpdateJson);
    }

    @POST
    @Path("/market/update-maker-delta")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public DeltasJson updateMakerDelta(MakerDeltalUpdateJson makerDeltalUpdateJson) {
        return commonUIService.updateMakerDelta(makerDeltalUpdateJson);
    }
}
