package com.bitplay.externalapi;

import com.bitplay.model.Leverage;
import com.bitplay.model.Pos;
import com.bitplay.model.ex.OrderResultTiny;
import com.bitplay.xchange.currency.CurrencyPair;
import com.bitplay.xchange.dto.account.AccountInfoContracts;
import com.bitplay.xchange.dto.trade.LimitOrder;

import com.bitplay.xchange.dto.Order.OrderType;
import java.math.BigDecimal;
import java.util.List;

public interface PrivateApi {

    Pos getPos(String instrumentId);

    AccountInfoContracts getAccount(String currencyCode);

    /**
     * Create a new order
     */
//    OrderResultTiny order(LimitOrder order);
    OrderResultTiny limitOrder(String instrumentId, OrderType orderType, BigDecimal thePrice, BigDecimal amount,
                               BigDecimal leverage,
                               List<Object> extraFlags

    );
//    LimitOrder orderWithResult(LimitOrder order);

    OrderResultTiny cnlOrder(String instrumentId, String orderId);

    List<LimitOrder> getOpenLimitOrders(String instrumentId, CurrencyPair currencyPair);

    LimitOrder getLimitOrder(String instrumentId, String orderId, CurrencyPair currencyPair);

    Leverage getLeverage(String instrumentId);

    Leverage changeLeverage(String newCurrOrInstrId, String newLeverageStr);

    default OrderResultTiny moveLimitOrder(String instrumentId, String orderId, BigDecimal newPrice) {
        return new OrderResultTiny();
    }

//    OrderBook getOrderBook(String newCurrOrInstrId, String newLeverageStr);

    boolean notCreated();
}
