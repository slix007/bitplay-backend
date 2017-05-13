package org.knowm.xchange.bitmex;

import java.io.IOException;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;

import io.swagger.client.model.Margin;
import io.swagger.client.model.User;
import io.swagger.client.model.Wallet;
import si.mazi.rescu.ParamsDigest;
import si.mazi.rescu.SynchronizedValueFactory;

/**
 * Created by Sergey Shurmin on 5/10/17.
 */
@Path("/api/v1")
@Produces(MediaType.APPLICATION_JSON)
public interface BitmexAuthenitcatedApi {

    @GET
    @Path("/user")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    User account(@HeaderParam("api-key") String apiKey,
                 @HeaderParam("api-signature") ParamsDigest signer,
                 @HeaderParam("api-nonce") SynchronizedValueFactory<Long> nonce) throws IOException;

    @GET
    @Path("/user/wallet")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    Wallet wallet(@HeaderParam("api-key") String apiKey,
                  @HeaderParam("api-signature") ParamsDigest signer,
                  @HeaderParam("api-nonce") SynchronizedValueFactory<Long> nonce,
                  @QueryParam("currency") String currency
    ) throws IOException;

    @GET
    @Path("/user/margin")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    Margin margin(@HeaderParam("api-key") String apiKey,
                  @HeaderParam("api-signature") ParamsDigest signer,
                  @HeaderParam("api-nonce") SynchronizedValueFactory<Long> nonce,
                  @QueryParam("currency") String currency
    ) throws IOException;


}