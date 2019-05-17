package org.knowm.xchange.bitmex.service;

import io.swagger.client.model.Execution;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.knowm.xchange.Exchange;
import org.knowm.xchange.bitmex.BitmexAdapters;
import org.knowm.xchange.bitmex.BitmexExchange;
import org.knowm.xchange.bitmex.dto.ArrayListWithHeaders;
import org.knowm.xchange.bitmex.dto.OrderWithHeaders;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.dto.Order;
import org.knowm.xchange.dto.Order.OrderStatus;
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
import si.mazi.rescu.HttpStatusIOException;

/**
 * Created by Sergey Shurmin on 5/18/17.
 */
public class BitmexTradeService extends BitmexTradeServiceRaw implements TradeService {

    public BitmexTradeService(Exchange exchange) {
        super(exchange);
    }

    @Override
    public OpenOrders getOpenOrders() throws ExchangeException, NotAvailableFromExchangeException, NotYetImplementedForExchangeException {
        throw new NotYetImplementedForExchangeException();
    }

    @Override
    public OpenOrders getOpenOrders(OpenOrdersParams params) throws ExchangeException, NotAvailableFromExchangeException, NotYetImplementedForExchangeException, IOException {
        final String filter = "{\"open\":true}"; //{"open": true}
        final String count = "10";

        final Collection<Order> orders = fetchOrders(filter, count);

        // open orders are always LimitOrder
        final List<LimitOrder> limitOrders = orders.stream()
                .map(order -> (LimitOrder) order)
                .collect(Collectors.toList());
        return new OpenOrders(limitOrders);
    }

    @Override
    public String placeMarketOrder(MarketOrder marketOrder)
            throws ExchangeException, NotAvailableFromExchangeException, NotYetImplementedForExchangeException, IOException {
        throw new NotAvailableFromExchangeException();
//        final String symbol = (String) exchange.getExchangeSpecification().getExchangeSpecificParametersItem("Symbol");
//        final String side = marketOrder.getType() == Order.OrderType.BID ? "Buy" : "Sell";
//        final Double tradableAmount = marketOrder.getTradableAmount().setScale(0, BigDecimal.ROUND_HALF_UP).doubleValue();
//        final io.swagger.client.model.Order order = bitmexAuthenitcatedApi
//                .order(exchange.getExchangeSpecification().getApiKey(), signatureCreator, exchange.getNonceFactory(),
//                        symbol,
//                        side,
//                        tradableAmount,
//                        "Market");
//
//        return String.valueOf(order.getOrderID());
    }

    public MarketOrder placeMarketOrderBitmex(MarketOrder marketOrder, String symbol) throws ExchangeException, NotAvailableFromExchangeException, NotYetImplementedForExchangeException, IOException {
        final Order mappedOrder = placeOrder(marketOrder, symbol, "Market", false, null, false);
        return (MarketOrder) mappedOrder;
    }

    public LimitOrder placeMarketOrderFillOrKill(MarketOrder marketOrder, String symbol, BigDecimal thePrice, Integer scale)
            throws ExchangeException, NotAvailableFromExchangeException, NotYetImplementedForExchangeException, IOException {
        final Double limitPrice = thePrice.setScale(scale, BigDecimal.ROUND_HALF_UP).doubleValue();
        final Order mappedOrder = placeOrder(marketOrder, symbol, "Limit", false, limitPrice, true);
        return (LimitOrder) mappedOrder;
    }

    @SuppressWarnings("unchecked")
    private Order placeOrder(Order orderToPlace, String symbol, String ordType, boolean participateDoNotInitiate, Double limitPrice,
            boolean fillOrKill) throws IOException {
        final Map<CurrencyPair, Integer> currencyToScale = (Map<CurrencyPair, Integer>) exchange.getExchangeSpecification()
                .getExchangeSpecificParametersItem("currencyToScale");
        final String side = orderToPlace.getType() == Order.OrderType.BID ? "Buy" : "Sell";
        final Double tradableAmount = orderToPlace.getTradableAmount().setScale(0, BigDecimal.ROUND_HALF_UP).doubleValue();
        final OrderWithHeaders resOrder;
        try {
            resOrder = bitmexAuthenitcatedApi.postOrder(
                    exchange.getExchangeSpecification().getApiKey(),
                    signatureCreator,
                    exchange.getNonceFactory(),
                    symbol,
                    side,
                    tradableAmount,
                    ordType,
                    limitPrice,
                    participateDoNotInitiate ? "ParticipateDoNotInitiate" : "",
                    fillOrKill ? "FillOrKill" : "");

        } catch (HttpStatusIOException e) {
            final BitmexStateService bitmexStateService = ((BitmexExchange) exchange).getBitmexStateService();
            bitmexStateService.setXrateLimit(e);
            throw e;
        }
        final BitmexStateService bitmexStateService = ((BitmexExchange) exchange).getBitmexStateService();
        bitmexStateService.setXrateLimit(resOrder);

        return BitmexAdapters.adaptOrder(resOrder, currencyToScale);
    }

    @Override
    public String placeLimitOrder(LimitOrder limitOrder) throws ExchangeException, NotAvailableFromExchangeException, NotYetImplementedForExchangeException, IOException {
        throw new NotAvailableFromExchangeException();
//        final String symbol = (String) exchange.getExchangeSpecification().getExchangeSpecificParametersItem("Symbol");
//        final Integer scale = (Integer) exchange.getExchangeSpecification().getExchangeSpecificParametersItem("Scale");
//        final String side = limitOrder.getType() == Order.OrderType.BID ? "Buy" : "Sell";
//        final Double tradableAmount = limitOrder.getTradableAmount().setScale(0, BigDecimal.ROUND_HALF_UP).doubleValue();
//        final Double limitPrice = limitOrder.getLimitPrice().setScale(scale, BigDecimal.ROUND_HALF_UP).doubleValue();
//        final io.swagger.client.model.Order order = bitmexAuthenitcatedApi.order(
//                exchange.getExchangeSpecification().getApiKey(),
//                signatureCreator,
//                exchange.getNonceFactory(),
//                symbol,
//                side,
//                tradableAmount,
//                limitPrice,
//                "Limit",
//                "ParticipateDoNotInitiate");
//        if (order.getOrdStatus().equals("CANCELLED")) {
//            throw new ExchangeException("Order has been cancelled");
//        }
//
//        return String.valueOf(order.getOrderID());
    }

    public LimitOrder placeLimitOrderBitmex(LimitOrder limitOrder, boolean participateDoNotInitiate, String symbol, Integer scale)
            throws ExchangeException, NotAvailableFromExchangeException, NotYetImplementedForExchangeException, IOException {

        final Double limitPrice = limitOrder.getLimitPrice().setScale(scale, BigDecimal.ROUND_HALF_UP).doubleValue();

        final Order mappedOrder = placeOrder(limitOrder, symbol, "Limit", participateDoNotInitiate, limitPrice, false);
        return (LimitOrder) mappedOrder;
    }

    @SuppressWarnings("unchecked")
    public LimitOrder moveLimitOrder(LimitOrder limitOrder, BigDecimal bestMakerPrice) throws ExchangeException, NotAvailableFromExchangeException, NotYetImplementedForExchangeException, IOException {
        final String symbol = (String) exchange.getExchangeSpecification().getExchangeSpecificParametersItem("Symbol");
        final Integer scale = (Integer) exchange.getExchangeSpecification().getExchangeSpecificParametersItem("Scale");
        final Map<CurrencyPair, Integer> currencyToScale = (Map<CurrencyPair, Integer>) exchange.getExchangeSpecification()
                .getExchangeSpecificParametersItem("currencyToScale");
        final String side = limitOrder.getType() == Order.OrderType.BID ? "Buy" : "Sell";
        final Double newPrice = bestMakerPrice.setScale(scale, BigDecimal.ROUND_HALF_UP).doubleValue();
        final OrderWithHeaders order;
        try {
            order = bitmexAuthenitcatedApi.updateOrder(
                    exchange.getExchangeSpecification().getApiKey(), signatureCreator, exchange.getNonceFactory(),
                    limitOrder.getId(),
                    symbol,
                    side,
                    newPrice,
                    "Limit",
                    "ParticipateDoNotInitiate");
        } catch (HttpStatusIOException e) {
            final BitmexStateService bitmexStateService = ((BitmexExchange) exchange).getBitmexStateService();
            bitmexStateService.setXrateLimit(e);
            throw e;
        }
        final BitmexStateService bitmexStateService = ((BitmexExchange) exchange).getBitmexStateService();
        bitmexStateService.setXrateLimit(order);

        // Updated fields: price. It also has: orderID, timestamp(also it has transactTime), ordStatus
        return BitmexAdapters.updateLimitOrder(limitOrder, order, currencyToScale);
    }

    @SuppressWarnings("unchecked")
    @Override
    public boolean cancelOrder(String orderId) throws ExchangeException, NotAvailableFromExchangeException, NotYetImplementedForExchangeException, IOException {
        final LimitOrder cancelledLimitOrder = cancelLimitOrder(orderId);
        return cancelledLimitOrder.getStatus() == OrderStatus.CANCELED
                || cancelledLimitOrder.getStatus() == OrderStatus.PENDING_CANCEL
                || cancelledLimitOrder.getStatus() == OrderStatus.FILLED
                || cancelledLimitOrder.getStatus() == OrderStatus.REJECTED
                || cancelledLimitOrder.getStatus() == OrderStatus.EXPIRED
                || cancelledLimitOrder.getStatus() == OrderStatus.STOPPED;
    }

    @SuppressWarnings("unchecked")
    public LimitOrder cancelLimitOrder(String orderId)
            throws ExchangeException, NotAvailableFromExchangeException, NotYetImplementedForExchangeException, IOException {
        final Map<CurrencyPair, Integer> currencyToScale = (Map<CurrencyPair, Integer>) exchange.getExchangeSpecification()
                .getExchangeSpecificParametersItem("currencyToScale");
        final ArrayListWithHeaders<io.swagger.client.model.Order> orders;
        try {
            orders = bitmexAuthenitcatedApi.deleteOrder(
                    exchange.getExchangeSpecification().getApiKey(), signatureCreator, exchange.getNonceFactory(),
                    orderId,
                    "",
                    "");
        } catch (HttpStatusIOException e) {
            final BitmexStateService bitmexStateService = ((BitmexExchange) exchange).getBitmexStateService();
            bitmexStateService.setXrateLimit(e);
            throw e;
        }
        final BitmexStateService bitmexStateService = ((BitmexExchange) exchange).getBitmexStateService();
        bitmexStateService.setXrateLimit(orders);

        return orders.stream()
                .map(order -> (LimitOrder) BitmexAdapters.adaptOrder(order, true, currencyToScale))
                .findFirst()
                .orElse(null);
    }

    @SuppressWarnings("unchecked")
    public List<LimitOrder> cancelAllOrders() throws ExchangeException, IOException {
        final String symbol = (String) exchange.getExchangeSpecification().getExchangeSpecificParametersItem("Symbol");
        final Map<CurrencyPair, Integer> currencyToScale = (Map<CurrencyPair, Integer>) exchange.getExchangeSpecification()
                .getExchangeSpecificParametersItem("currencyToScale");

        final ArrayListWithHeaders<io.swagger.client.model.Order> orders;
        try {
            orders = bitmexAuthenitcatedApi.deleteAllOrders(
                    exchange.getExchangeSpecification().getApiKey(), signatureCreator, exchange.getNonceFactory(),
                    symbol,
                    "",
                    "");
        } catch (HttpStatusIOException e) {
            final BitmexStateService bitmexStateService = ((BitmexExchange) exchange).getBitmexStateService();
            bitmexStateService.setXrateLimit(e);
            throw e;
        }
        final BitmexStateService bitmexStateService = ((BitmexExchange) exchange).getBitmexStateService();
        bitmexStateService.setXrateLimit(orders);

        return orders.stream()
                .map(order -> (LimitOrder) BitmexAdapters.adaptOrder(order, true, currencyToScale))
                .collect(Collectors.toList());
    }

    @SuppressWarnings("unchecked")
    public Order closeAllPos(String symbol) throws IOException {
        final Map<CurrencyPair, Integer> currencyToScale = (Map<CurrencyPair, Integer>) exchange.getExchangeSpecification()
                .getExchangeSpecificParametersItem("currencyToScale");
        final OrderWithHeaders resOrder;
        try {
            resOrder = bitmexAuthenitcatedApi.marketOrderToCloseAllPos(
                    exchange.getExchangeSpecification().getApiKey(),
                    signatureCreator,
                    exchange.getNonceFactory(),
                    symbol,
                    "Market",
                    "Close");

        } catch (HttpStatusIOException e) {
            final BitmexStateService bitmexStateService = ((BitmexExchange) exchange).getBitmexStateService();
            bitmexStateService.setXrateLimit(e);
            throw e;
        }
        final BitmexStateService bitmexStateService = ((BitmexExchange) exchange).getBitmexStateService();
        bitmexStateService.setXrateLimit(resOrder);

        return BitmexAdapters.adaptOrder(resOrder, currencyToScale);
    }

    @Override
    public UserTrades getTradeHistory(TradeHistoryParams params) {
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

    @SuppressWarnings("unchecked")
    @Override
    public Collection<Order> getOrder(String... orderIds) throws ExchangeException, NotAvailableFromExchangeException, NotYetImplementedForExchangeException, IOException {
        final String filter = "{\"orderID\":\"" + orderIds[0] + "\"}"; //{"orderID": "0c8f1e6f-5a06-a8a8-6abf-96ebdecea95f"};
        final String count = "1";

        return fetchOrders(filter, count);
    }

    @SuppressWarnings("unchecked")
    private Collection<Order> fetchOrders(String filter, String count) throws IOException {
        final ArrayListWithHeaders<io.swagger.client.model.Order> orders;
        final Map<CurrencyPair, Integer> currencyToScale = (Map<CurrencyPair, Integer>) exchange.getExchangeSpecification()
                .getExchangeSpecificParametersItem("currencyToScale");

        try {
            orders = bitmexAuthenitcatedApi.getOrders(
                    exchange.getExchangeSpecification().getApiKey(),
                    signatureCreator,
                    exchange.getNonceFactory(),
                    filter,
                    count
            );
            final BitmexStateService bitmexStateService = ((BitmexExchange) exchange).getBitmexStateService();
            bitmexStateService.setXrateLimit(orders);
        } catch (HttpStatusIOException e) {
            final BitmexStateService bitmexStateService = ((BitmexExchange) exchange).getBitmexStateService();
            bitmexStateService.setXrateLimit(e);
            throw e;
        }
        return orders.stream()
                .map(order -> BitmexAdapters.adaptOrder(order, currencyToScale))
                .collect(Collectors.toList());
    }

    public Collection<Execution> getOrderParts(String orderId) throws IOException {
        String filter = "{\"orderID\":\"" + orderId + "\"}"; //{"orderID": "0c8f1e6f-5a06-a8a8-6abf-96ebdecea95f"};
        final ArrayListWithHeaders<Execution> executionList;
        try {
            executionList = bitmexAuthenitcatedApi.getTradeHistory(
                    exchange.getExchangeSpecification().getApiKey(),
                    signatureCreator,
                    exchange.getNonceFactory(),
                    filter
            );
        } catch (HttpStatusIOException e) {
            final BitmexStateService bitmexStateService = ((BitmexExchange) exchange).getBitmexStateService();
            bitmexStateService.setXrateLimit(e);
            throw e;
        }
        final BitmexStateService bitmexStateService = ((BitmexExchange) exchange).getBitmexStateService();
        bitmexStateService.setXrateLimit(executionList);
        return executionList;
    }
//
//    public List<Instrument> getFunding() throws IOException {
//        final String symbol = (String) exchange.getExchangeSpecification().getExchangeSpecificParametersItem("Symbol");
//        String columns = "[\"fundingRate\",\"fundingTimestamp\"]";
//
//        return bitmexAuthenitcatedApi.instrument(
//                exchange.getExchangeSpecification().getApiKey(),
//                signatureCreator,
//                exchange.getNonceFactory(),
//                symbol,
//                columns
//        );
//    }
}
