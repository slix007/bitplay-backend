package org.knowm.xchange.bitmex.service;

import org.knowm.xchange.Exchange;
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
        throw new NotYetImplementedForExchangeException();
    }

    @Override
    public String placeMarketOrder(MarketOrder marketOrder) throws ExchangeException, NotAvailableFromExchangeException, NotYetImplementedForExchangeException, IOException {
        throw new NotYetImplementedForExchangeException();
    }

    @Override
    public String placeLimitOrder(LimitOrder limitOrder) throws ExchangeException, NotAvailableFromExchangeException, NotYetImplementedForExchangeException, IOException {
        final String symbol = "XBTUSD";//BitmexAdapters.adaptSymbol(limitOrder.getCurrencyPair());
        final String side = limitOrder.getType() == Order.OrderType.BID ? "Buy" : "Sell";
        final Double tradableAmount = limitOrder.getTradableAmount().setScale(8, BigDecimal.ROUND_HALF_UP).doubleValue();
        final Double limitPrice = limitOrder.getLimitPrice().setScale(1, BigDecimal.ROUND_HALF_UP).doubleValue();
        final io.swagger.client.model.Order order = bitmexAuthenitcatedApi.order(exchange.getExchangeSpecification().getApiKey(), signatureCreator, exchange.getNonceFactory(),
                symbol,
                side,
                tradableAmount,
                limitPrice,
                "Limit",
                "ParticipateDoNotInitiate");

        return String.valueOf(order.getOrderID());
    }

    public String moveLimitOrder(LimitOrder limitOrder, BigDecimal bestMakerPrice) throws ExchangeException, NotAvailableFromExchangeException, NotYetImplementedForExchangeException, IOException {
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

        return String.valueOf(order.getOrderID());
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
        throw new NotYetImplementedForExchangeException();
    }
}
