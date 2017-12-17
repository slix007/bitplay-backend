package org.knowm.xchange.bitmex.service;

import org.knowm.xchange.Exchange;
import org.knowm.xchange.bitmex.BitmexAdapters;
import org.knowm.xchange.dto.Order;
import org.knowm.xchange.dto.trade.LimitOrder;
import org.knowm.xchange.dto.trade.MarketOrder;
import org.knowm.xchange.dto.trade.OpenOrders;
import org.knowm.xchange.dto.trade.UserTrades;
import org.knowm.xchange.exceptions.ExchangeException;
import org.knowm.xchange.exceptions.NotAvailableFromExchangeException;
import org.knowm.xchange.exceptions.NotYetImplementedForExchangeException;
import org.knowm.xchange.service.trade.TradeService;
import org.knowm.xchange.service.trade.params.TradeHistoryParams;
import org.knowm.xchange.service.trade.params.orders.OpenOrdersParams;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

import io.swagger.client.model.Instrument;

/**
 * Created by Sergey Shurmin on 5/18/17.
 */
public class BitmexTradeService extends BitmexTradeServiceRaw implements TradeService {

    public BitmexTradeService(Exchange exchange) {
        super(exchange);
    }

    @Override
    public OpenOrders getOpenOrders() throws ExchangeException, NotAvailableFromExchangeException, NotYetImplementedForExchangeException, IOException {
        throw new NotYetImplementedForExchangeException();
    }

    @Override
    public OpenOrders getOpenOrders(OpenOrdersParams params) throws ExchangeException, NotAvailableFromExchangeException, NotYetImplementedForExchangeException, IOException {
        String filter = "{\"open\":true}"; //{"open": true}
        String count = "10";
        final List<io.swagger.client.model.Order> orders = bitmexAuthenitcatedApi.getOrders(
                exchange.getExchangeSpecification().getApiKey(),
                signatureCreator,
                exchange.getNonceFactory(),
                filter,
                count
        );
        final List<LimitOrder> limitOrders = orders.stream()
                .map(order -> (LimitOrder) BitmexAdapters.adaptOrder(order))
                .collect(Collectors.toList());
        return new OpenOrders(limitOrders);
    }

    @Override
    public String placeMarketOrder(MarketOrder marketOrder) throws ExchangeException, NotAvailableFromExchangeException, NotYetImplementedForExchangeException, IOException {
        final String symbol = "XBTUSD";//BitmexAdapters.adaptSymbol(limitOrder.getCurrencyPair());
        final String side = marketOrder.getType() == Order.OrderType.BID ? "Buy" : "Sell";
        final Double tradableAmount = marketOrder.getTradableAmount().setScale(0, BigDecimal.ROUND_HALF_UP).doubleValue();
        final io.swagger.client.model.Order order = bitmexAuthenitcatedApi.order(exchange.getExchangeSpecification().getApiKey(), signatureCreator, exchange.getNonceFactory(),
                symbol,
                side,
                tradableAmount,
                "Market");

        return String.valueOf(order.getOrderID());
    }

    public MarketOrder placeMarketOrderBitmex(MarketOrder marketOrder) throws ExchangeException, NotAvailableFromExchangeException, NotYetImplementedForExchangeException, IOException {
        final String symbol = "XBTUSD";//BitmexAdapters.adaptSymbol(limitOrder.getCurrencyPair());
        final String side = marketOrder.getType() == Order.OrderType.BID ? "Buy" : "Sell";
        final Double tradableAmount = marketOrder.getTradableAmount().setScale(0, BigDecimal.ROUND_HALF_UP).doubleValue();
        final io.swagger.client.model.Order order = bitmexAuthenitcatedApi.order(exchange.getExchangeSpecification().getApiKey(), signatureCreator, exchange.getNonceFactory(),
                symbol,
                side,
                tradableAmount,
                "Market");

        return (MarketOrder) BitmexAdapters.adaptOrder(order);
    }

    @Override
    public String placeLimitOrder(LimitOrder limitOrder) throws ExchangeException, NotAvailableFromExchangeException, NotYetImplementedForExchangeException, IOException {
        final String symbol = "XBTUSD";//BitmexAdapters.adaptSymbol(limitOrder.getCurrencyPair());
        final String side = limitOrder.getType() == Order.OrderType.BID ? "Buy" : "Sell";
        final Double tradableAmount = limitOrder.getTradableAmount().setScale(0, BigDecimal.ROUND_HALF_UP).doubleValue();
        final Double limitPrice = limitOrder.getLimitPrice().setScale(1, BigDecimal.ROUND_HALF_UP).doubleValue();
        final io.swagger.client.model.Order order = bitmexAuthenitcatedApi.order(exchange.getExchangeSpecification().getApiKey(), signatureCreator, exchange.getNonceFactory(),
                symbol,
                side,
                tradableAmount,
                limitPrice,
                "Limit",
                "ParticipateDoNotInitiate");
        if (order.getOrdStatus().equals("CANCELLED")) {
            throw new ExchangeException("Order has been cancelled");
        }

        return String.valueOf(order.getOrderID());
    }

    public LimitOrder placeLimitOrderBitmex(LimitOrder limitOrder) throws ExchangeException, NotAvailableFromExchangeException, NotYetImplementedForExchangeException, IOException {
        final String symbol = "XBTUSD";//BitmexAdapters.adaptSymbol(limitOrder.getCurrencyPair());
        final String side = limitOrder.getType() == Order.OrderType.BID ? "Buy" : "Sell";
        final Double tradableAmount = limitOrder.getTradableAmount().setScale(0, BigDecimal.ROUND_HALF_UP).doubleValue();
        final Double limitPrice = limitOrder.getLimitPrice().setScale(1, BigDecimal.ROUND_HALF_UP).doubleValue();
        final io.swagger.client.model.Order order = bitmexAuthenitcatedApi.order(exchange.getExchangeSpecification().getApiKey(), signatureCreator, exchange.getNonceFactory(),
                symbol,
                side,
                tradableAmount,
                limitPrice,
                "Limit",
                "ParticipateDoNotInitiate");

        return (LimitOrder) BitmexAdapters.adaptOrder(order);
    }

    public LimitOrder moveLimitOrder(LimitOrder limitOrder, BigDecimal bestMakerPrice) throws ExchangeException, NotAvailableFromExchangeException, NotYetImplementedForExchangeException, IOException {
        final String symbol = "XBTUSD";//BitmexAdapters.adaptSymbol(limitOrder.getCurrencyPair());
        final String side = limitOrder.getType() == Order.OrderType.BID ? "Buy" : "Sell";
        final Double newPrice = bestMakerPrice.setScale(1, BigDecimal.ROUND_HALF_UP).doubleValue();
        final io.swagger.client.model.Order order = bitmexAuthenitcatedApi.updateOrder(
                exchange.getExchangeSpecification().getApiKey(), signatureCreator, exchange.getNonceFactory(),
                limitOrder.getId(),
                symbol,
                side,
                newPrice,
                "Limit",
                "ParticipateDoNotInitiate");

        // Updated fields: price. It also has: orderID, timestamp(also it has transactTime), ordStatus
        return order == null ? null : BitmexAdapters.updateLimitOrder(limitOrder, order);
    }

    @Override
    public boolean cancelOrder(String orderId) throws ExchangeException, NotAvailableFromExchangeException, NotYetImplementedForExchangeException, IOException {
        throw new NotYetImplementedForExchangeException();
    }

    @Override
    public UserTrades getTradeHistory(TradeHistoryParams params) throws IOException {
        throw new NotYetImplementedForExchangeException();
    }

    @Override
    public TradeHistoryParams createTradeHistoryParams() {
        throw new NotYetImplementedForExchangeException();
    }

    @Override
    public OpenOrdersParams createOpenOrdersParams() {
        throw new NotYetImplementedForExchangeException();
    }

    @Override
    public Collection<Order> getOrder(String... orderIds) throws ExchangeException, NotAvailableFromExchangeException, NotYetImplementedForExchangeException, IOException {
        String filter = "{\"orderID\":\"" + orderIds[0] + "\"}"; //{"orderID": "0c8f1e6f-5a06-a8a8-6abf-96ebdecea95f"};
        String count = "1";
        final List<io.swagger.client.model.Order> orders = bitmexAuthenitcatedApi.getOrders(
                exchange.getExchangeSpecification().getApiKey(),
                signatureCreator,
                exchange.getNonceFactory(),
                filter,
                count
        );
        return orders.stream()
                .map(BitmexAdapters::adaptOrder)
                .collect(Collectors.toList());
    }

    public List<Instrument> getFunding() throws IOException {
        final String symbol = "XBTUSD";
        String columns = "[\"fundingRate\",\"fundingTimestamp\"]";

        return bitmexAuthenitcatedApi.instrument(
                exchange.getExchangeSpecification().getApiKey(),
                signatureCreator,
                exchange.getNonceFactory(),
                symbol,
                columns
        );


    }
}
