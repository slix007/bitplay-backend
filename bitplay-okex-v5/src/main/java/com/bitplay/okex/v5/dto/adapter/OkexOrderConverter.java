package com.bitplay.okex.v5.dto.adapter;

import com.bitplay.model.ex.OrderResultTiny;
import com.bitplay.okex.v5.dto.param.Order;
import com.bitplay.okex.v5.dto.result.OrderResult;
import com.bitplay.okex.v5.dto.result.OrderResult.OrderResultData;
import com.bitplay.okex.v5.enums.FuturesOrderTypeEnum;
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
                .side(convertSide(limitOrder.getType()))
//                .leverage(BigDecimal.valueOf(20))
                .sz(limitOrder.getTradableAmount().intValue())
                .instId(convertInstrumentId(limitOrder.getCurrencyPair()))
                .px(limitOrder.getLimitPrice())
                .build();
    }

    private String convertInstrumentId(CurrencyPair currencyPair) {
        return "";
    }

    private static String convertSide(OrderType orderType) {
        if (orderType == OrderType.BID || orderType == OrderType.EXIT_ASK) {
            return "buy";
        }
        if (orderType == OrderType.ASK || orderType == OrderType.EXIT_BID) {
            return "sell";
        }
        return "";
    }

    public static Order createOrder(String instrumentId, OrderType orderType, BigDecimal thePrice, BigDecimal tradeableAmount,
            FuturesOrderTypeEnum orderTypeEnum, BigDecimal leverage) {
        return Order.builder()
                .instId(instrumentId)
//                .leverage(leverage)
                .side(convertSide(orderType))
                .px(thePrice)
                .sz(tradeableAmount.intValue())
                .ordType(orderTypeEnum.code())
                .build();
    }

    public static OrderResultTiny convertOrderResult(OrderResult o) {
        if (o.getData() != null && o.getData().size() > 0) {
            final OrderResultData r = o.getData().get(0);
            return new OrderResultTiny(
                    r.getClOrdId(),
                    r.getOrdId(),
                    r.getSCode() != null && r.getSCode().equals("0"),
                    r.getSCode(),
                    r.getSMsg()
            );
        }
        return null;
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

