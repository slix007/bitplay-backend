package com.bitplay.xchangestream.bitmex.dto;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import com.bitplay.xchange.currency.CurrencyPair;
import com.bitplay.xchange.dto.Order;
import com.bitplay.xchange.dto.marketdata.OrderBook;
import com.bitplay.xchange.dto.trade.LimitOrder;

/**
 * Created by Sergey Shurmin on 5/7/17.
 */
public class BitmexStreamAdapters {

    public static OrderBook adaptBitmexOrderBook(BitmexDepth bitmexDepth, CurrencyPair currencyPair) {
        final Date timestamp = bitmexDepth.getTimestamp();
        List<LimitOrder> asks = adaptLimitOrders(bitmexDepth.getAsks(), Order.OrderType.ASK, currencyPair, timestamp);
        List<LimitOrder> bids = adaptLimitOrders(bitmexDepth.getBids(), Order.OrderType.BID, currencyPair, timestamp);

        return new OrderBook(timestamp, bitmexDepth.getReceiveTimestamp(), asks, bids);
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

    /**
     * Create new orderBook.
     * <p>{@link OrderBook#getAsks()} and {@link OrderBook#getBids()} are synchronizedList</p>
     */
    public static OrderBook adaptBitmexOrderBook(BitmexOrderBook bitmexOrderBook, CurrencyPair currencyPair) {
        final Date timestamp = new Date(bitmexOrderBook.getGettingTimeEpochMs());
        List<LimitOrder> asks = Collections.synchronizedList(new ArrayList<>());
        List<LimitOrder> bids = Collections.synchronizedList(new ArrayList<>());

        insertAllItems(bitmexOrderBook, timestamp, currencyPair, bids, asks);

        return new OrderBook(timestamp, asks, bids);
    }

    private static Order.OrderType toOrderType(BitmexOrder.Side side) {
        return side == BitmexOrder.Side.Sell ? Order.OrderType.ASK : Order.OrderType.BID; // Sell, Buy
    }

    private static LimitOrder toLimitOrder(BitmexOrder bitmexOrder, Date timestamp, CurrencyPair currencyPair) {
        final Order.OrderType orderType = toOrderType(bitmexOrder.getSide());
        return new LimitOrder(orderType,
                bitmexOrder.getSize(),
                currencyPair,
                String.valueOf(bitmexOrder.getId()),
                timestamp,
                bitmexOrder.getPrice());
    }

    /**
     * Thread-safe delete orders from orderBook.
     * <p>Synchronyzed on each {@link OrderBook#getAsks()} and {@link OrderBook#getBids()}</p>
     */
    public static OrderBook delete(OrderBook orderBook, BitmexOrderBook bitmexOrderBook) {
        final List<LimitOrder> bids = orderBook.getBids();
        final List<LimitOrder> asks = orderBook.getAsks();
        final Set<String> idToRemove = bitmexOrderBook.getBitmexOrderList().stream()
                .map(BitmexOrder::getId)
                .map(String::valueOf)
                .collect(Collectors.toSet());

        synchronized (bids) {
            bids.removeIf(limitOrder -> idToRemove.contains(limitOrder.getId()));
        }

        synchronized (asks) {
            asks.removeIf(limitOrder -> idToRemove.contains(limitOrder.getId()));
        }

        return orderBook;
    }

    public static OrderBook cloneOrderBook(OrderBook ob, Date gettingTimestamp) {
        return new OrderBook(gettingTimestamp,
                Collections.synchronizedList(ob.getAsks().stream().map(BitmexStreamAdapters::cloneLimitOrder).collect(Collectors.toList())),
                Collections.synchronizedList(ob.getBids().stream().map(BitmexStreamAdapters::cloneLimitOrder).collect(Collectors.toList()))
        );
    }

    private static LimitOrder cloneLimitOrder(LimitOrder l) {
        final LimitOrder limitOrder = new LimitOrder(l.getType(), l.getTradableAmount(), l.getCurrencyPair(), l.getId(),
                l.getTimestamp(), l.getLimitPrice(), l.getAveragePrice(), l.getCumulativeAmount(), l.getStatus());
        limitOrder.setOrderFlags(l.getOrderFlags());
        return limitOrder;
    }

    /**
     * Thread-safe update orders from orderBook.
     * <p>Synchronyzed on each {@link OrderBook#getAsks()} and {@link OrderBook#getBids()}</p>
     */
    public static OrderBook update(OrderBook orderBook, BitmexOrderBook bitmexOrderBook, Date timestamp, CurrencyPair currencyPair) {
        final List<LimitOrder> bids = orderBook.getBids();
        final List<LimitOrder> asks = orderBook.getAsks();

        final List<LimitOrder> bidsToUpdate = bitmexOrderBook.getBitmexOrderList().stream()
                .filter(bitmexOrder -> toOrderType(bitmexOrder.getSide()) == Order.OrderType.BID)
                .map(bitmexOrder -> toLimitOrder(bitmexOrder, timestamp, currencyPair))
                .collect(Collectors.toList());

        final List<LimitOrder> asksToUpdate = bitmexOrderBook.getBitmexOrderList().stream()
                .filter(bitmexOrder -> toOrderType(bitmexOrder.getSide()) == Order.OrderType.ASK)
                .map(bitmexOrder -> toLimitOrder(bitmexOrder, timestamp, currencyPair))
                .collect(Collectors.toList());

        synchronized (bids) {
            bids.replaceAll(limitOrder -> updateAnOrder(limitOrder, bidsToUpdate));
        }

        synchronized (asks) {
            asks.replaceAll(limitOrder -> updateAnOrder(limitOrder, asksToUpdate));
        }

        return orderBook;
    }

    private static LimitOrder updateAnOrder(LimitOrder limitOrder, List<LimitOrder> updates) {
        for (LimitOrder anUpdate : updates) {
            if (anUpdate.getId().equals(limitOrder.getId())) {
                return new LimitOrder(anUpdate.getType(),
                        anUpdate.getTradableAmount() != null ? anUpdate.getTradableAmount() : limitOrder.getTradableAmount(),
                        anUpdate.getCurrencyPair(),
                        anUpdate.getId(),
                        anUpdate.getTimestamp(),
                        anUpdate.getLimitPrice() != null ? anUpdate.getLimitPrice() : limitOrder.getLimitPrice());
            }
        }
        return limitOrder;
    }

    /**
     * Thread-safe insert orders from orderBook.
     * <p>Synchronyzed on each {@link OrderBook#getAsks()} and {@link OrderBook#getBids()}</p>
     */
    public static OrderBook insert(OrderBook orderBook, BitmexOrderBook bitmexOrderBook, Date timestamp, CurrencyPair currencyPair) {
        final List<LimitOrder> bids = orderBook.getBids();
        final List<LimitOrder> asks = orderBook.getAsks();

        insertAllItems(bitmexOrderBook, timestamp, currencyPair, bids, asks);
        return orderBook;
    }

    private static void insertAllItems(BitmexOrderBook bitmexOrderBook, Date timestamp, CurrencyPair currencyPair, List<LimitOrder> bids, List<LimitOrder> asks) {
        final List<LimitOrder> bidsToInsert = bitmexOrderBook.getBitmexOrderList().stream()
                .filter(bitmexOrder -> toOrderType(bitmexOrder.getSide()) == Order.OrderType.BID)
                .map(bitmexOrder -> toLimitOrder(bitmexOrder, timestamp, currencyPair))
                .collect(Collectors.toList());

        final List<LimitOrder> asksToInsert = bitmexOrderBook.getBitmexOrderList().stream()
                .filter(bitmexOrder -> toOrderType(bitmexOrder.getSide()) == Order.OrderType.ASK)
                .map(bitmexOrder -> toLimitOrder(bitmexOrder, timestamp, currencyPair))
                .collect(Collectors.toList());

        synchronized (bids) {
            bids.addAll(bidsToInsert);
            bids.sort((o1, o2) -> o2.getLimitPrice().compareTo(o1.getLimitPrice()));
        }

        synchronized (asks) {
            asks.addAll(asksToInsert);
            asks.sort(Comparator.comparing(LimitOrder::getLimitPrice));
        }
    }
}
