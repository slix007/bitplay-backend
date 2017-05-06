package org.knowm.xchange.bitmex;

import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.dto.Order;
import org.knowm.xchange.dto.marketdata.OrderBook;
import org.knowm.xchange.dto.trade.LimitOrder;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

import io.swagger.client.model.OrderBookL2;

/**
 * Created by Sergey Shurmin on 5/3/17.
 */
public class BitmexAdapters {
    private final static String BID_TYPE = "Buy";
    private final static String ASK_TYPE = "Sell";

    public static OrderBook adoptBitmexOrderBook(List<OrderBookL2> bitmexMarketDepth, CurrencyPair currencyPair) {
        List<LimitOrder> asks = adaptBitmexPublicOrders(bitmexMarketDepth, Order.OrderType.ASK, currencyPair);
        List<LimitOrder> bids = adaptBitmexPublicOrders(bitmexMarketDepth, Order.OrderType.BID, currencyPair);


        return new OrderBook(null, asks, bids);
    }

    private static List<LimitOrder> adaptBitmexPublicOrders(List<OrderBookL2> bitmexMarketDepth,
                                                            Order.OrderType orderType, CurrencyPair currencyPair) {
        List<LimitOrder> limitOrderList = new ArrayList<LimitOrder>();

        for (OrderBookL2 orderBookL2 : bitmexMarketDepth) {

            if ((orderBookL2.getSide().equals(BID_TYPE) && orderType.equals(Order.OrderType.BID))
                    || (orderBookL2.getSide().equals(ASK_TYPE) && orderType.equals(Order.OrderType.ASK))) {

                LimitOrder limitOrder = new LimitOrder
                        .Builder(orderType, currencyPair)
                        .tradableAmount(orderBookL2.getSize())
                        .limitPrice(new BigDecimal(orderBookL2.getPrice()).setScale(1, RoundingMode.HALF_UP))
                        .build();
                limitOrderList.add(limitOrder);
            }
        }

        return limitOrderList;
    }
}
