package com.bitplay.okex.v3.service.swap;

import com.bitplay.model.Pos;
import com.bitplay.model.ex.OrderResultTiny;
import com.bitplay.okex.v3.ApiConfiguration;
import com.bitplay.okex.v3.service.swap.api.SwapTradeApiServiceImpl;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.dto.Order;
import org.knowm.xchange.dto.account.AccountInfoContracts;
import org.knowm.xchange.dto.trade.LimitOrder;

import java.math.BigDecimal;
import java.util.List;

public class SwapPrivateApi extends SwapTradeApiServiceImpl {

    public SwapPrivateApi(ApiConfiguration config) {
        super(config);
    }

    @Override
    public Pos getPos(String instrumentId) {
        return null;
    }

    @Override
    public AccountInfoContracts getAccount(String currencyCode) {
        return null;
    }

    @Override
    public OrderResultTiny limitOrder(String instrumentId, Order.OrderType orderType, BigDecimal thePrice, BigDecimal amount, BigDecimal leverage,
                                      List<Object> extraFlags) {
        return null;
    }

    @Override
    public OrderResultTiny cnlOrder(String instrumentId, String orderId) {
        return null;
    }

    @Override
    public List<LimitOrder> getOpenLimitOrders(String instrumentId, CurrencyPair currencyPair) {
        return null;
    }

    @Override
    public LimitOrder getLimitOrder(String instrumentId, String orderId, CurrencyPair currencyPair) {
        return null;
    }
}
