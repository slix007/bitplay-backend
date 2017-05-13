package org.knowm.xchange.bitmex;

import java.io.IOException;
import java.util.List;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;

import io.swagger.client.model.OrderBookL2;

/**
 * Created by Sergey Shurmin on 5/10/17.
 */
@Path("/api/v1")
@Produces(MediaType.APPLICATION_JSON)
public interface BitmexPublicApi {

    @GET
    @Path("/orderBook/L2")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    public List<OrderBookL2> getOrderBook(
            @QueryParam("symbol") String symbol,
            @QueryParam("depth") Integer depth) throws IOException;


}
