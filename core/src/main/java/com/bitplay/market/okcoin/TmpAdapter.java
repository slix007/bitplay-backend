package com.bitplay.market.okcoin;

import info.bitrich.xchangestream.okexv3.dto.privatedata.OkExUserOrder;
import java.util.ArrayList;
import java.util.List;
import org.knowm.xchange.currency.Currency;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.dto.Order.OrderStatus;
import org.knowm.xchange.dto.Order.OrderType;
import org.knowm.xchange.dto.trade.LimitOrder;
import org.knowm.xchange.okcoin.OkCoinAdapters;

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
                    okExUserOrder.getContractVal(),
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

}
