package com.bitplay.okex.v5.dto.adapter;

import com.bitplay.model.ex.OrderResultTiny;
import com.bitplay.okex.v5.dto.param.Order;
import com.bitplay.okex.v5.dto.result.OrderResult;
import com.bitplay.okex.v5.enums.FuturesOrderTypeEnum;
import com.bitplay.okex.v5.enums.FuturesTransactionTypeEnum;
import com.bitplay.okex.v5.utils.OkexErrors;
import com.bitplay.xchange.currency.CurrencyPair;
import com.bitplay.xchange.dto.Order.OrderType;
import com.bitplay.xchange.dto.trade.LimitOrder;
import java.math.BigDecimal;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class OkexOrderConverter implements SuperConverter<LimitOrder, Order> {

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

    private static Integer convertType(OrderType orderType) {
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

    public static Order createOrder(String instrumentId, OrderType orderType, BigDecimal thePrice, BigDecimal tradeableAmount,
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

    public static OrderResultTiny convertOrderResult(OrderResult o) {
        return new OrderResultTiny(o.getClient_oid(), o.getOrder_id(), o.isResult(), o.getError_code(), o.getError_message());
    }

    public static String getErrorCodeTranslation(OrderResultTiny result) {
        String errorCodeTranslation = "";
        if (result != null && result.getError_code() != null) { // Example: result.getDetails() == "Code: 20015"
            String errorCode = result.getError_code();
            try {
                errorCodeTranslation = OkexErrors.getErrorMessage(Integer.parseInt(errorCode));
            } catch (NumberFormatException e) {
                log.error("can not translate code " + errorCode);
            }
        }
        return errorCodeTranslation;
    }


}

