package com.bitplay.okex.v3.service.swap.api;

import com.bitplay.externalapi.PrivateApi;
import com.bitplay.okex.v3.ApiConfiguration;
import com.bitplay.okex.v3.client.ApiClient;
import com.bitplay.okex.v3.dto.futures.result.Account;
import com.bitplay.okex.v3.dto.futures.result.OkexSwapAllPositions;
import com.bitplay.okex.v3.dto.futures.result.OkexSwapOnePosition;
import com.bitplay.okex.v3.dto.futures.result.SwapAccounts;

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
//
//    @Override
//    public Account getAccountsByCurrency(String currency) {
//        throw new IllegalArgumentException("not supported");
//    }
//
//    @Override
//    public SwapAccounts getAccountsByInstrumentId(String instrumentId) {
//        return this.client.executeSync(this.api.getAccountsByInstrumentId(instrumentId));
//    }
//
//    public Object testAccount(String currency) {
//        return this.client.executeSync(this.api.testAccount(currency));
//    }
//
//
//    @Override
//    public OrderResult order(Order order) {
//        return this.client.executeSync(this.api.order(order));
//    }
//
//    @Override
//    public OrderResult cancelOrder(String instrumentId, String orderId) {
//        return this.client.executeSync(this.api.cancelOrder(instrumentId, orderId));
//    }
//
//    @Override
//    public ClosePositionResult closePosition(ClosePosition closePosition) {
//        return null;
////        final ClosePositionResult closePositionResult = this.client.executeSync(this.api.closePosition(closePosition));
////        return closePositionResult;
//    }
//
//    @Override
//    public OrderDetail getOrder(String instrumentId, String orderId) {
//        return this.client.executeSync(this.api.getOrder(instrumentId, orderId));
//    }
//
//    @Override
//    public List<OrderDetail> getOpenOrders(String instrumentId) {
//        // Order Status: -2 = Failed -1 = Canceled 0 = Open 1 = Partially Filled 2 = Completely Filled 3 = Submitting 4 = Canceling
//        // 6 = Incomplete (open + partially filled)
//        // 7 = Complete (canceled + completely filled)
//        final OpenOrdersResult openOrdersResult = this.client.executeSync(this.api.getOrdersWithState(instrumentId, 6));
//        return openOrdersResult.getOrder_info();
//    }
//
//    @Override
//    public LeverageResult getInstrumentLeverRate(String currency) {
//        return this.client.executeSync(this.api.getLeverRate(currency));
//    }
//
//    @Override
//    public LeverageResult changeLeverageOnCross(String instrumentId, String leverage) {
//        return this.client.executeSync(this.api.changeLeverageOnCross(instrumentId, new LeverageCross(leverage)));
//    }
}
