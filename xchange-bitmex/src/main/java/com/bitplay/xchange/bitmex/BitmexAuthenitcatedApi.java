package com.bitplay.xchange.bitmex;

import com.bitplay.xchange.bitmex.dto.ArrayListWithHeaders;
import com.bitplay.xchange.bitmex.dto.OrderWithHeaders;
import io.swagger.client.model.Execution;
import io.swagger.client.model.Instrument;
import io.swagger.client.model.Order;
import io.swagger.client.model.Position;
import si.mazi.rescu.ParamsDigest;
import si.mazi.rescu.SynchronizedValueFactory;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import java.io.IOException;

/**
 * Created by Sergey Shurmin on 5/10/17.
 */
@Path("/api/v1")
@Produces(MediaType.APPLICATION_JSON)
public interface BitmexAuthenitcatedApi {

    // "X-RateLimit-Limit" is 300

//    @GET
//    @Path("/user")
//    User account(@HeaderParam("api-key") String apiKey,
//                 @HeaderParam("api-signature") ParamsDigest signer,
//                 @HeaderParam("api-nonce") SynchronizedValueFactory<Long> nonce) throws IOException;

//    @GET
//    @Path("/user/wallet")
//    Wallet wallet(@HeaderParam("api-key") String apiKey,
//                  @HeaderParam("api-signature") ParamsDigest signer,
//                  @HeaderParam("api-nonce") SynchronizedValueFactory<Long> nonce,
//                  @QueryParam("currency") String currency
//    ) throws IOException;

//    @GET
//    @Path("/user/margin")
//    Margin margin(@HeaderParam("api-key") String apiKey,
//                  @HeaderParam("api-signature") ParamsDigest signer,
//                  @HeaderParam("api-nonce") SynchronizedValueFactory<Long> nonce,
//                  @QueryParam("currency") String currency
//    ) throws IOException;

    @GET
    @Path("/instrument")
    ArrayListWithHeaders<Instrument> instrument(
            @HeaderParam("api-key") String apiKey,
            @HeaderParam("api-signature") ParamsDigest signer,
            @HeaderParam("api-expires") SynchronizedValueFactory<Long> nonce,
            @QueryParam("symbol") String symbol,
            @QueryParam("columns") String columns
    ) throws IOException;

    @GET
    @Path("/order")
    ArrayListWithHeaders<Order> getOrders(
            @HeaderParam("api-key") String apiKey,
            @HeaderParam("api-signature") ParamsDigest signer,
            @HeaderParam("api-expires") SynchronizedValueFactory<Long> nonce,
            @QueryParam("filter") String filter,
            @QueryParam("count") String count
    ) throws IOException;

    @POST
    @Path("/order")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    OrderWithHeaders postOrder(
            @HeaderParam("api-key") String apiKey,
            @HeaderParam("api-signature") ParamsDigest signer,
            @HeaderParam("api-expires") SynchronizedValueFactory<Long> nonce,
            @FormParam("symbol") String symbol,
            @FormParam("side") String side,
            @FormParam("orderQty") Double orderQty,
            @FormParam("ordType") String ordType,
            @FormParam("price") Double price,
            @FormParam("execInst") String execInst,
            @FormParam("timeInForce") String timeInForce
    ) throws IOException;

    @POST
    @Path("/order")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    OrderWithHeaders marketOrder(
            @HeaderParam("api-key") String apiKey,
            @HeaderParam("api-signature") ParamsDigest signer,
            @HeaderParam("api-expires") SynchronizedValueFactory<Long> nonce,
            @FormParam("symbol") String symbol,
            @FormParam("side") String side,
            @FormParam("orderQty") Double orderQty,
            @FormParam("ordType") String ordType
    ) throws IOException;

    @POST
    @Path("/order")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    OrderWithHeaders marketOrderToCloseAllPos(
            @HeaderParam("api-key") String apiKey,
            @HeaderParam("api-signature") ParamsDigest signer,
            @HeaderParam("api-expires") SynchronizedValueFactory<Long> nonce,
            @FormParam("symbol") String symbol,
            @FormParam("ordType") String ordType,
            @FormParam("execInst") String execInst
    ) throws IOException;


    @PUT
    @Path("/order")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    OrderWithHeaders updateOrder(
            @HeaderParam("api-key") String apiKey,
            @HeaderParam("api-signature") ParamsDigest signer,
            @HeaderParam("api-expires") SynchronizedValueFactory<Long> nonce,
            @FormParam("orderID") String orderID,
            @FormParam("symbol") String symbol,
            @FormParam("side") String side,
            @FormParam("price") Double price,
            @FormParam("ordType") String ordType,
            @FormParam("execInst") String execInst
    ) throws IOException;

    @DELETE
    @Path("/order")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    ArrayListWithHeaders<Order> deleteOrder(
            @HeaderParam("api-key") String apiKey,
            @HeaderParam("api-signature") ParamsDigest signer,
            @HeaderParam("api-expires") SynchronizedValueFactory<Long> nonce,
            @FormParam("orderID") String orderID,
            @FormParam("clOrdID") String clOrdID,
            @FormParam("text") String text
    ) throws IOException;

    @DELETE
    @Path("/order/all")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    ArrayListWithHeaders<Order> deleteAllOrders(
            @HeaderParam("api-key") String apiKey,
            @HeaderParam("api-signature") ParamsDigest signer,
            @HeaderParam("api-expires") SynchronizedValueFactory<Long> nonce,
            @FormParam("symbol") String symbol,
            @FormParam("filter") String filter,
            @FormParam("text") String text
    ) throws IOException;

    @GET
    @Path("/position")
    ArrayListWithHeaders<Position> position(
            @HeaderParam("api-key") String apiKey,
            @HeaderParam("api-signature") ParamsDigest signer,
            @HeaderParam("api-expires") SynchronizedValueFactory<Long> nonce) throws IOException;

    @GET
    @Path("/execution/tradeHistory")
    ArrayListWithHeaders<Execution> getTradeHistory(
            @HeaderParam("api-key") String apiKey,
            @HeaderParam("api-signature") ParamsDigest signer,
            @HeaderParam("api-expires") SynchronizedValueFactory<Long> nonce,
            @QueryParam("filter") String filter
    ) throws IOException;
}
