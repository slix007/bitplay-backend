package info.bitrich.xchangestream.bitmex.dto;

import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.dto.Order;
import org.knowm.xchange.dto.marketdata.OrderBook;
import org.knowm.xchange.dto.trade.LimitOrder;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;

/**
 * Created by Sergey Shurmin on 5/7/17.
 */
public class BitmexStreamAdapters {

    public static OrderBook adaptBitmexOrderBook(BitmexDepth bitmexDepth, CurrencyPair currencyPair) {
        final Date timestamp = bitmexDepth.getTimestamp();
        List<LimitOrder> asks = adaptLimitOrders(bitmexDepth.getAsks(), Order.OrderType.ASK, currencyPair, timestamp);
        Collections.reverse(asks); //TODO do we need it?
        List<LimitOrder> bids = adaptLimitOrders(bitmexDepth.getBids(), Order.OrderType.BID, currencyPair, timestamp);

        return new OrderBook(timestamp, asks, bids);
    }

    private static List<LimitOrder> adaptLimitOrders(BigDecimal[][] list, Order.OrderType type, CurrencyPair currencyPair, Date timestamp) {
        List<LimitOrder> limitOrders = new ArrayList<>(list.length);
        for (BigDecimal[] data : list) {
            limitOrders.add(adaptLimitOrder(type, data, currencyPair, null, timestamp));
        }
        return limitOrders;
    }

    private static LimitOrder adaptLimitOrder(Order.OrderType type, BigDecimal[] data, CurrencyPair currencyPair, String id, Date timestamp) {

        return new LimitOrder(type, data[1], currencyPair, id, timestamp, data[0]);
    }
}
