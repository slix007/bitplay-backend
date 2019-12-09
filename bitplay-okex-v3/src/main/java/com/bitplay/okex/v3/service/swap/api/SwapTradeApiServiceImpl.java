package com.bitplay.okex.v3.service.swap.api;

import com.bitplay.externalapi.PrivateApi;
import com.bitplay.okex.v3.ApiConfiguration;
import com.bitplay.okex.v3.client.ApiClient;
import com.bitplay.okex.v3.dto.futures.param.Order;
import com.bitplay.okex.v3.dto.futures.result.LeverageResult;
import com.bitplay.okex.v3.dto.futures.result.OkexSwapAllPositions;
import com.bitplay.okex.v3.dto.futures.result.OkexSwapOnePosition;
import com.bitplay.okex.v3.dto.futures.result.OpenOrdersResult;
import com.bitplay.okex.v3.dto.futures.result.OrderDetail;
import com.bitplay.okex.v3.dto.futures.result.OrderResult;
import com.bitplay.okex.v3.dto.futures.result.SwapAccounts;
import com.bitplay.okex.v3.dto.swap.SwapLeverageCross;

import java.util.List;

/**
 * Futures trade api
 */
public abstract class SwapTradeApiServiceImpl implements PrivateApi {

    private ApiClient client;
    private SwapTradeApi api;

    public SwapTradeApiServiceImpl(ApiConfiguration config) {
        this.client = new ApiClient(config);
        this.api = client.createService(SwapTradeApi.class);
    }

    public OkexSwapAllPositions getPositions() {
        return this.client.executeSync(this.api.getPositions());
    }

    public Object testPositions() {
        return this.client.executeSync(this.api.testPositions());
    }

    public OkexSwapOnePosition getInstrumentPositionApi(String instrumentId) {
        return this.client.executeSync(this.api.getInstrumentPosition(instrumentId));
    }

    public Object testInstrumentPosition(String instrumentId) {
        return this.client.executeSync(this.api.testInstrumentPosition(instrumentId));
    }

    public SwapAccounts getAccountsByInstrumentApi(String instrumentId) {
        return this.client.executeSync(this.api.getAccountByInstrumentId(instrumentId));
    }

    public Object testAccountsByInstrumentApi(String instrumentId) {
        return this.client.executeSync(this.api.testAccountByInstrumentId(instrumentId));
    }

    public OrderResult orderApi(Order order) {
        return this.client.executeSync(this.api.order(order));
    }

    public OrderResult cancelOrder(String instrumentId, String orderId) {
        return this.client.executeSync(this.api.cancelOrder(instrumentId, orderId));
    }

    public OrderDetail getOrder(String instrumentId, String orderId) {
        return this.client.executeSync(this.api.getOrder(instrumentId, orderId));
    }

    public List<OrderDetail> getOpenOrders(String instrumentId) {
        // Order Status: -2 = Failed -1 = Canceled 0 = Open 1 = Partially Filled 2 = Completely Filled 3 = Submitting 4 = Canceling
        // 6 = Incomplete (open + partially filled)
        // 7 = Complete (canceled + completely filled)
        final OpenOrdersResult openOrdersResult = this.client.executeSync(this.api.getOrdersWithState(instrumentId, 6));
        return openOrdersResult.getOrder_info();
    }

    public LeverageResult getInstrumentLeverRate(String instrumentId) {
        return this.client.executeSync(this.api.getLeverRate(instrumentId));
    }

    public LeverageResult changeLeverageOnCross(String instrumentId, String leverage) {
        return this.client.executeSync(this.api.changeLeverageOnCross(instrumentId, new SwapLeverageCross(leverage, "3")));
    }

    @Override
    public boolean notCreated() {
        return this.client == null || this.api == null;
    }

}
