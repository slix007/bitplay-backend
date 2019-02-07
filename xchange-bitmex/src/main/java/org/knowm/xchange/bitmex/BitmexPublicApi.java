package org.knowm.xchange.bitmex;

import io.swagger.client.model.OrderBookL2;
import java.io.IOException;
import java.util.List;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import org.knowm.xchange.bitmex.dto.BitmexInfoDto;

/**
 * Created by Sergey Shurmin on 5/10/17.
 */
@Path("/api/v1")
@Produces(MediaType.APPLICATION_JSON)
public interface BitmexPublicApi {

    @GET
    @Path("/orderBook/L2")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    List<OrderBookL2> getOrderBook( // not in use. See Websocket API.
            @QueryParam("symbol") String symbol,
            @QueryParam("depth") Integer depth) throws IOException;

    // "X-RateLimit-Limit" is  150
    @GET
    @Path("/")
    @Produces(MediaType.APPLICATION_JSON)
    BitmexInfoDto getGenralInfo() throws IOException;

}
