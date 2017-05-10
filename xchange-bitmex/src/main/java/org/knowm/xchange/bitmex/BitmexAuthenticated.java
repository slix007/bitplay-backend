package org.knowm.xchange.bitmex;

import org.knowm.xchange.Exchange;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.ws.rs.core.GenericType;

import io.swagger.client.ApiException;
import io.swagger.client.Pair;
import io.swagger.client.api.UserApi;
import io.swagger.client.model.User;
import io.swagger.client.model.Wallet;

/**
 * Created by Sergey Shurmin on 5/3/17.
 */
public class BitmexAuthenticated extends Bitmex {

    protected final Exchange exchange;
    protected final BitmexSignatureCreator signatureCreator;
    protected final String apikey;
    protected final String secretKey;

    private final UserApi userApi;
    private Object wallets;

    public BitmexAuthenticated(Exchange exchange) {
        super();
        this.exchange = exchange;

        apikey = exchange.getExchangeSpecification().getApiKey();
        secretKey = exchange.getExchangeSpecification().getSecretKey();

        signatureCreator = new BitmexSignatureCreator(apikey, secretKey);

//        apiClient.setApiKey(apikey);
//        apiClient.set

        userApi = new UserApi(apiClient);
    }

    public Wallet getWallet(String currency) throws ApiException {

        String token = null;
        final String verb = "GET";
        final String path = "/api/v1/user";
        final String nonce = exchange.getNonceFactory().createValue().toString();
        final String signature = signatureCreator.generateBitmexSignature(verb, path, nonce);

        apiClient.addDefaultHeader("api-nonce", nonce);
        apiClient.addDefaultHeader("api-key", apikey);
        apiClient.addDefaultHeader("api-signature", signature);
/*
        try {
            final Content content = Request.Get("https://www.bitmex.com/api/v1/user")
                    .addHeader("api-nonce", nonce)
                    .addHeader("api-key", apikey)
                    .addHeader("api-signature", signature)
                    .addHeader("Content-Type", MediaType.APPLICATION_JSON)
                    .execute()
                    .returnContent();
            System.out.println(content);
        } catch (IOException e) {
            e.printStackTrace();
        }
*/
        final User user = userApi.userGet();
        System.out.println(user.getEmail());

        Wallet response = userApi.userGetWallet(currency);
        return response;
    }

    /**
     * Get your user model.
     *
     * @return User
     * @throws ApiException if fails to make API call
     */
    public User userGet() throws ApiException {
        Object localVarPostBody = null;

        // create path and map variables
        String localVarPath = "/user".replaceAll("\\{format\\}", "json");

        // query params
        List<Pair> localVarQueryParams = new ArrayList<Pair>();
        Map<String, String> localVarHeaderParams = new HashMap<String, String>();
        Map<String, Object> localVarFormParams = new HashMap<String, Object>();


        final String[] localVarAccepts = {
                "application/json", "application/xml", "text/xml", "application/javascript", "text/javascript"
        };
        final String localVarAccept = apiClient.selectHeaderAccept(localVarAccepts);

        final String[] localVarContentTypes = {
                "application/json", "application/x-www-form-urlencoded"
        };
        final String localVarContentType = apiClient.selectHeaderContentType(localVarContentTypes);

        String[] localVarAuthNames = new String[]{};

        GenericType<User> localVarReturnType = new GenericType<User>() {
        };
        return apiClient.invokeAPI(localVarPath, "GET", localVarQueryParams, localVarPostBody, localVarHeaderParams, localVarFormParams, localVarAccept, localVarContentType, localVarAuthNames, localVarReturnType);
    }
}
