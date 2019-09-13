package com.bitplay.okex.v3.service.futures.impl;

import com.bitplay.okex.v3.ApiConfiguration;
import com.bitplay.okex.v3.client.ApiClient;
import com.bitplay.okex.v3.dto.futures.param.LeverageCross;
import com.bitplay.okex.v3.dto.futures.param.Order;
import com.bitplay.okex.v3.dto.futures.result.Accounts;
import com.bitplay.okex.v3.dto.futures.result.LeverageResult;
import com.bitplay.okex.v3.dto.futures.result.OkexPositions;
import com.bitplay.okex.v3.dto.futures.result.OrderResult;
import com.bitplay.okex.v3.service.futures.FuturesTradeApiService;
import com.fasterxml.jackson.databind.util.JSONPObject;

/**
 * Futures trade api
 *
 * @author Tony Tian
 * @version 1.0.0
 * @date 2018/3/9 18:52
 */
public class FuturesTradeApiServiceImpl implements FuturesTradeApiService {

    private ApiClient client;
    private FuturesTradeApi api;

    public FuturesTradeApiServiceImpl(ApiConfiguration config) {
        this.client = new ApiClient(config);
        this.api = client.createService(FuturesTradeApi.class);
    }

    @Override
    public OkexPositions getPositions() {
        return this.client.executeSync(this.api.getPositions());
    }

    @Override
    public Accounts getAccounts() {
        return this.client.executeSync(this.api.getAccounts());
    }


    @Override
    public OrderResult order(Order order) {
        return this.client.executeSync(this.api.order(order));
    }

    @Override
    public LeverageResult getInstrumentLeverRate(String currency) {
        return this.client.executeSync(this.api.getLeverRate(currency));
    }

    @Override
    public LeverageResult changeLeverageOnCross(String currency, String leverage) {
        return this.client.executeSync(this.api.changeLeverageOnCross(currency, new LeverageCross(leverage)));
    }
}
