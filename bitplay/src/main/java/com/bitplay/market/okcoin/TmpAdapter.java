package com.bitplay.market.okcoin;

import com.bitplay.okexv5.dto.privatedata.OkexStreamOrder;
import java.util.ArrayList;
import java.util.List;
import com.bitplay.xchange.currency.Currency;
import com.bitplay.xchange.currency.CurrencyPair;
import com.bitplay.xchange.dto.Order.OrderStatus;
import com.bitplay.xchange.dto.Order.OrderType;
import com.bitplay.xchange.dto.trade.LimitOrder;
import com.bitplay.xchange.okcoin.OkCoinAdapters;

public class TmpAdapter {

    public static List<LimitOrder> adaptTradeResult(OkexStreamOrder[] okexStreamOrders) {
        List<LimitOrder> res = new ArrayList();
        OkexStreamOrder[] var2 = okexStreamOrders;
        int var3 = okexStreamOrders.length;

        for(int var4 = 0; var4 < var3; ++var4) {
            OkexStreamOrder okexStreamOrder = var2[var4];
            OrderType orderType = OkCoinAdapters.adaptOrderType(okexStreamOrder.getSide());
            CurrencyPair currencyPair = parseCurrencyPair(okexStreamOrder.getInstrumentId());
            OrderStatus orderStatus = OkCoinAdapters.convertStatus(okexStreamOrder.getState());
            LimitOrder limitOrder = new LimitOrder(orderType,
                    okexStreamOrder.getSize(),
                    currencyPair,
                    okexStreamOrder.getOrderId(),
                    okexStreamOrder.getTimestamp(),
                    okexStreamOrder.getPrice(),
                    okexStreamOrder.getPriceAvg(),
                    okexStreamOrder.getFilledQty(),
                    orderStatus);
            res.add(limitOrder);
        }

        return res;
    }

    private static CurrencyPair parseCurrencyPair(String instrumentId) { // instrumentId BTC-USD-170317
        final String[] split = instrumentId.split("-");
        final String base = split[0];
        final String counter = split[1];
        return new CurrencyPair(Currency.getInstance(base), Currency.getInstance(counter));
    }

    public static LimitOrder cloneWithId(LimitOrder input, String newId) {
        return new LimitOrder(
                input.getType(),
                input.getTradableAmount(),
                input.getCurrencyPair(),
                newId,
                input.getTimestamp(),
                input.getLimitPrice());
    }

}
