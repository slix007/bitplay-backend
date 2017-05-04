package org.knowm.xchange.bitmex;

import java.math.BigDecimal;
import java.util.List;

import io.swagger.client.ApiException;
import io.swagger.client.api.OrderBookApi;
import io.swagger.client.api.UserApi;
import io.swagger.client.model.OrderBookL2;
import io.swagger.client.model.User;
import io.swagger.client.model.Wallet;

/**
 * Created by Sergey Shurmin on 5/3/17.
 */
public class BitmexAuthenticated extends Bitmex {

    private final UserApi userApi;
    private Object wallets;

    public BitmexAuthenticated() {
        super();
        userApi = new UserApi(apiClient);
    }

    public Wallet getWallet(String currency) throws ApiException {
        Wallet response = userApi.userGetWallet(currency);
        return response;
    }
}
