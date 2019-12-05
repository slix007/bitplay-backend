package com.bitplay.okex.v3.service.futures.api;

import com.bitplay.okex.v3.ApiConfiguration;
import com.bitplay.okex.v3.client.ApiClient;
import com.bitplay.okex.v3.dto.futures.param.ClosePosition;
import com.bitplay.okex.v3.dto.futures.param.LeverageCross;
import com.bitplay.okex.v3.dto.futures.param.Order;
import com.bitplay.okex.v3.dto.futures.result.Account;
import com.bitplay.okex.v3.dto.futures.result.Accounts;
import com.bitplay.okex.v3.dto.futures.result.ClosePositionResult;
import com.bitplay.okex.v3.dto.futures.result.LeverageResult;
import com.bitplay.okex.v3.dto.futures.result.OkexAllPositions;
import com.bitplay.okex.v3.dto.futures.result.OkexOnePosition;
import com.bitplay.okex.v3.dto.futures.result.OpenOrdersResult;
import com.bitplay.okex.v3.dto.futures.result.OrderDetail;
import com.bitplay.okex.v3.dto.futures.result.OrderResult;
import com.bitplay.okex.v3.dto.futures.result.SwapAccounts;

import java.util.List;

/**
 * Futures trade api
 *
 * @author Tony Tian
 * @version 1.0.0
 * @date 2018/3/9 18:52
 */
public abstract class FuturesTradeApiServiceImpl implements FuturesTradeApiService {

    private ApiClient client;
    private FuturesTradeApi api;

    public FuturesTradeApiServiceImpl(ApiConfiguration config) {
        this.client = new ApiClient(config);
        this.api = client.createService(FuturesTradeApi.class);
    }

    @Override
    public OkexAllPositions getPositionsApi() {
        return this.client.executeSync(this.api.getPositions());
    }

    @Override
    public Object testPositionsApi() {
        return this.client.executeSync(this.api.testPositions());
    }

    @Override
    public OkexOnePosition getInstrumentPositionApi(String instrumentId) {
        return this.client.executeSync(this.api.getInstrumentPosition(instrumentId));
    }
    public Object getInstrumentPositionTest(String instrumentId) {
        return this.client.executeSync(this.api.getInstrumentPositionTest(instrumentId));
    }

    @Override
    public Object testInstrumentPositionApi(String instrumentId) {
        return this.client.executeSync(this.api.testInstrumentPosition(instrumentId));
    }

    @Override
    public Accounts getAccountsApi() {
        return this.client.executeSync(this.api.getAccounts());
    }

    @Override
    public Account getAccountsByCurrencyApi(String currency) {
        return this.client.executeSync(this.api.getAccountsByCurrency(currency));
    }

    @Override
    public SwapAccounts getAccountsByInstrumentId(String instrumentId) {
        throw new IllegalArgumentException("not supported");
    }

    public Object testAccount(String currency) {
        return this.client.executeSync(this.api.testAccount(currency));
    }


    @Override
    public OrderResult orderApi(Order order) {
        return this.client.executeSync(this.api.order(order));
    }

    @Override
    public OrderResult cancelOrder(String instrumentId, String orderId) {
        return this.client.executeSync(this.api.cancelOrder(instrumentId, orderId));
    }

    @Override
    public ClosePositionResult closePosition(ClosePosition closePosition) {
        final ClosePositionResult closePositionResult = this.client.executeSync(this.api.closePosition(closePosition));
        return closePositionResult;
    }

    @Override
    public OrderDetail getOrder(String instrumentId, String orderId) {
        return this.client.executeSync(this.api.getOrder(instrumentId, orderId));
    }

    @Override
    public List<OrderDetail> getOpenOrders(String instrumentId) {
        // Order Status: -2 = Failed -1 = Canceled 0 = Open 1 = Partially Filled 2 = Completely Filled 3 = Submitting 4 = Canceling
        // 6 = Incomplete (open + partially filled)
        // 7 = Complete (canceled + completely filled)
        final OpenOrdersResult openOrdersResult = this.client.executeSync(this.api.getOrdersWithState(instrumentId, 6));
        return openOrdersResult.getOrder_info();
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