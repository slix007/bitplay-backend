package com.bitplay.market.okcoin.convert;

import com.bitplay.okex.v3.dto.futures.param.Order;
import com.bitplay.okex.v3.enums.FuturesOrderTypeEnum;
import com.bitplay.okex.v3.enums.FuturesTransactionTypeEnum;
import java.math.BigDecimal;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.dto.Order.OrderType;
import org.knowm.xchange.dto.trade.LimitOrder;

public class LimitOrderToOrderConverter implements SuperConverter<LimitOrder, Order> {

    @Override
    public Order apply(LimitOrder limitOrder) {
        return Order.builder()
                .type(convertType(limitOrder.getType()))
                .leverage(BigDecimal.valueOf(20))
                .size(limitOrder.getTradableAmount().intValue())
                .instrument_id(convertInstrumentId(limitOrder.getCurrencyPair()))
                .price(limitOrder.getLimitPrice())
                .build();
    }

    private String convertInstrumentId(CurrencyPair currencyPair) {
        return "";
    }

    public Integer convertType(OrderType orderType) {
        // 1:open long 2:open short 3:close long 4:close short
        if (orderType == OrderType.BID) {
            return FuturesTransactionTypeEnum.OPEN_LONG.code();
        }
        if (orderType == OrderType.ASK) {
            return FuturesTransactionTypeEnum.OPEN_SHORT.code();
        }
        if (orderType == OrderType.EXIT_BID) {
            return FuturesTransactionTypeEnum.CLOSE_LONG.code();
        }
        if (orderType == OrderType.EXIT_ASK) {
            return FuturesTransactionTypeEnum.CLOSE_SHORT.code();
        }
        return 0;
     }

    public Order createOrder(String instrumentId, OrderType orderType, BigDecimal thePrice, BigDecimal tradeableAmount,
            FuturesOrderTypeEnum orderTypeEnum, BigDecimal leverage) {
        return Order.builder()
                .instrument_id(instrumentId)
                .leverage(leverage)
                .type(convertType(orderType))
                .price(thePrice)
                .size(tradeableAmount.intValue())
                .order_type(orderTypeEnum.code())
                .build();
    }
}

