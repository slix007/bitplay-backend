package com.bitplay.market.okcoin;

import com.bitplay.okexv5.dto.privatedata.OkExUserOrder;
import java.util.ArrayList;
import java.util.List;
import com.bitplay.xchange.currency.Currency;
import com.bitplay.xchange.currency.CurrencyPair;
import com.bitplay.xchange.dto.Order.OrderStatus;
import com.bitplay.xchange.dto.Order.OrderType;
import com.bitplay.xchange.dto.trade.LimitOrder;
import com.bitplay.xchange.okcoin.OkCoinAdapters;

public class TmpAdapter {

    public static List<LimitOrder> adaptTradeResult(OkExUserOrder[] okExUserOrders) {
        List<LimitOrder> res = new ArrayList();
        OkExUserOrder[] var2 = okExUserOrders;
        int var3 = okExUserOrders.length;

        for(int var4 = 0; var4 < var3; ++var4) {
            OkExUserOrder okExUserOrder = var2[var4];
            OrderType orderType = adaptOrderType(okExUserOrder.getType());
            CurrencyPair currencyPair = parseCurrencyPair(okExUserOrder.getInstrumentId());
            OrderStatus orderStatus = OkCoinAdapters.adaptOrderStatus(okExUserOrder.getStatus());
            LimitOrder limitOrder = new LimitOrder(orderType,
                    okExUserOrder.getSize(),
                    currencyPair,
                    okExUserOrder.getOrderId(),
                    okExUserOrder.getTimestamp(),
                    okExUserOrder.getPrice(),
                    okExUserOrder.getPriceAvg(),
                    okExUserOrder.getFilledQty(),
                    orderStatus);
            res.add(limitOrder);
        }

        return res;
    }

    private static OrderType adaptOrderType(String type) {
        if (type.equals("1")) {
            return OrderType.BID;
        } else if (type.equals("2")) {
            return OrderType.ASK;
        } else if (type.equals("3")) {
            return OrderType.EXIT_BID;
        } else {
            return type.equals("4") ? OrderType.EXIT_ASK : null;
        }
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
