package com.bitplay.externalapi;

import com.bitplay.model.Leverage;
import com.bitplay.model.Pos;
import com.bitplay.model.ex.OrderResultTiny;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.dto.Order;
import org.knowm.xchange.dto.account.AccountInfoContracts;
import org.knowm.xchange.dto.trade.LimitOrder;

import java.math.BigDecimal;
import java.util.List;

public interface PrivateApi {

    Pos getPos(String instrumentId);

    AccountInfoContracts getAccount(String currencyCode);

    /**
     * Create a new order
     */
//    OrderResultTiny order(LimitOrder order);
    OrderResultTiny limitOrder(String instrumentId, Order.OrderType orderType, BigDecimal thePrice, BigDecimal amount,
                               BigDecimal leverage,
                               List<Object> extraFlags

    );
//    LimitOrder orderWithResult(LimitOrder order);

    OrderResultTiny cnlOrder(String instrumentId, String orderId);

    List<LimitOrder> getOpenLimitOrders(String instrumentId, CurrencyPair currencyPair);

    LimitOrder getLimitOrder(String instrumentId, String orderId, CurrencyPair currencyPair);

    Leverage getLeverage(String instrumentId);

    Leverage changeLeverage(String newCurrOrInstrId, String newLeverageStr);

    boolean notCreated();
}
