package com.crypto.business.polonex;

import info.bitrich.xchangestream.poloniex.incremental.PoloniexWebSocketDepth;

import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.dto.Order;
import org.knowm.xchange.dto.marketdata.OrderBook;
import org.knowm.xchange.dto.trade.LimitOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Merge incremental updates of orderBook for Poloniex.
 * Created by Sergey Shurmin on 4/13/17.
 */
public class PoloniexOrderBookMerger {

    private static final Logger LOG = LoggerFactory.getLogger(PoloniexOrderBookMerger.class);

    public synchronized static OrderBook cleanOrderBook(OrderBook orderBook) {
        final List<LimitOrder> asks = cleanItems(orderBook.getAsks());
        final List<LimitOrder> bids = cleanItems(orderBook.getBids());
        return new OrderBook(new Date(), asks, bids);
    }

    private static List<LimitOrder> cleanItems(List<LimitOrder> asksOrBids) {
        final Date oneMinutePast = Date.from(Instant.now().minusSeconds(60));

        final List<LimitOrder> newCollect = asksOrBids.stream()
                .filter(limitOrder ->
                        !(limitOrder.getLimitPrice().compareTo(BigDecimal.ZERO) == 0
                                && limitOrder.getTimestamp().before(oneMinutePast)))
                .collect(Collectors.toList());

        return newCollect;
    }


    /**
     * It Uses LimitOrder.id to save the seq number.
     */
    public synchronized static OrderBook merge(OrderBook orderBook, PoloniexWebSocketDepth depthUpdate) {
        //TODO optimize it. Don't create a new orderBook, just replase data in the current?
        OrderBook newOrderBook = null;
        final PoloniexWebSocketDepth.DataObj data = depthUpdate.getData();
        final Long seq = depthUpdate.getSequence();
        final String depthUpdateType = depthUpdate.getType();
        final List<LimitOrder> bids = orderBook.getBids();
        final List<LimitOrder> asks = orderBook.getAsks();
        switch (depthUpdateType) {
            case "orderBookRemove":
                if (data.getType().equals("bid")) {
                    final List<LimitOrder> newBids = updateTheItem(bids, depthUpdate, true);
                    newOrderBook = new OrderBook(new Date(), asks, newBids);
                } else if (data.getType().equals("ask")) {
                    final List<LimitOrder> newAsks = updateTheItem(asks, depthUpdate, true);
                    newOrderBook = new OrderBook(new Date(), newAsks, bids);
                }
                break;
            case "orderBookModify":
                if (data.getType().equals("bid")) {
                    final List<LimitOrder> newBids = updateTheItem(bids, depthUpdate, false);
                    newOrderBook = new OrderBook(new Date(), asks, newBids);
                } else if (data.getType().equals("ask")) {
                    final List<LimitOrder> newAsks = updateTheItem(asks, depthUpdate, false);
                    newOrderBook = new OrderBook(new Date(), newAsks, bids);
                }

                break;
            case "newTrade":
                //do nothing
                break;
            default:
                throw new IllegalArgumentException("Unknown type " + depthUpdateType);
        }

        return newOrderBook;
    }

    private static List<LimitOrder> updateTheItem(List<LimitOrder> asksOrBids, PoloniexWebSocketDepth depthUpdate,
                                                  boolean setToZero) {
        final CurrencyPair currencyPair = asksOrBids.get(0).getCurrencyPair();
        final Order.OrderType orderType = asksOrBids.get(0).getType();
        final Long updateSeqNumber = depthUpdate.getSequence();

        // remove
        final List<LimitOrder> newCollect = asksOrBids.stream()
                .filter(limitOrder ->
                        // get only unequal prices
                        limitOrder.getLimitPrice().compareTo(depthUpdate.getData().getRate()) != 0)
                .collect(Collectors.toList());

        LimitOrder foundItem = findTheItem(asksOrBids, depthUpdate);

        if (setToZero) {
            if (foundItem == null) {
                LOG.warn("Have not found item to delete {},{}, {}",
                        depthUpdate.getData().getType(), depthUpdate.getData().getRate(), depthUpdate.getSequence());
            } else {
                LOG.debug("Deleted (Set to 0) {},{},{}", depthUpdate.getData().getType(), depthUpdate.getData().getRate(),
                        depthUpdate.getSequence());
            }
        } else {
            if (foundItem == null) {
                LOG.debug("Added {},{},{}", depthUpdate.getData().getType(), depthUpdate.getData().getRate(),
                        depthUpdate.getSequence());
            } else {
                LOG.debug("Modified {},{},{}", depthUpdate.getData().getType(), depthUpdate.getData().getRate(),
                        depthUpdate.getSequence());
            }
        }

        // add the price (or zero price)
        if (foundItem != null
                && foundItem.getId() != null
                && Long.valueOf(foundItem.getId()) > updateSeqNumber) {
            LOG.warn("Existing seq is bigger than updating seq {}>{}", foundItem.getId(), updateSeqNumber);
        } else {
            newCollect.add(new LimitOrder(orderType,
                    setToZero ? BigDecimal.ZERO : depthUpdate.getData().getAmount(),
                    currencyPair,
                    String.valueOf(depthUpdate.getSequence()),
                    new Date(),
                    depthUpdate.getData().getRate()
            ));
        }

        return cleanItems(newCollect);
    }

    private static LimitOrder findTheItem(List<LimitOrder> bidsOrAsks, PoloniexWebSocketDepth depthUpdate) {
        List<LimitOrder> collect = bidsOrAsks.stream()
                .filter(limitOrder -> limitOrder.getLimitPrice().compareTo(depthUpdate.getData().getRate()) == 0)
                .collect(Collectors.toList());
        if (collect.size() > 1) {
            LOG.error("More than one limitOrder with price {} found", depthUpdate.getData().getRate());
            collect = collect.stream()
                    .filter(limitOrder -> limitOrder.getTradableAmount().compareTo(BigDecimal.ZERO) != 0)
                    .collect(Collectors.toList());
        }

        if (collect.size() > 1) {
            LOG.error("More than one limitOrder with price {} found", depthUpdate.getData().getRate());
            LOG.error("limitPrice={}, amounts={}",
                    depthUpdate.getData().getRate(),
                    collect.stream()
                            .map(LimitOrder::getTradableAmount)
                            .map(BigDecimal::toString)
                            .reduce("", (a, b) -> a + " " + b));
        }

        return collect.size() > 0 ? collect.get(0) : null;
    }
}

