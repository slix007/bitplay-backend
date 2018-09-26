package org.knowm.xchange.bitmex;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeType;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.swagger.client.model.Margin;
import io.swagger.client.model.OrderBookL2;
import io.swagger.client.model.Position;
import io.swagger.client.model.Wallet;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import org.knowm.xchange.currency.Currency;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.dto.Order;
import org.knowm.xchange.dto.account.AccountInfoContracts;
import org.knowm.xchange.dto.account.Balance;
import org.knowm.xchange.dto.marketdata.OrderBook;
import org.knowm.xchange.dto.trade.LimitOrder;
import org.knowm.xchange.dto.trade.MarketOrder;
import org.knowm.xchange.dto.trade.OpenOrders;

/**
 * Created by Sergey Shurmin on 5/3/17.
 */
public class BitmexAdapters {

    private final static String BID_TYPE = "Buy";
    private final static String ASK_TYPE = "Sell";

    public static OrderBook adaptBitmexOrderBook(List<OrderBookL2> bitmexMarketDepth, CurrencyPair currencyPair, Integer scale) {
        List<LimitOrder> asks = adaptBitmexPublicOrders(bitmexMarketDepth, Order.OrderType.ASK, currencyPair, scale);
        List<LimitOrder> bids = adaptBitmexPublicOrders(bitmexMarketDepth, Order.OrderType.BID, currencyPair, scale);


        return new OrderBook(null, asks, bids);
    }

    private static List<LimitOrder> adaptBitmexPublicOrders(List<OrderBookL2> bitmexMarketDepth,
            Order.OrderType orderType, CurrencyPair currencyPair, Integer scale) {
        List<LimitOrder> limitOrderList = new ArrayList<>();

        for (OrderBookL2 orderBookL2 : bitmexMarketDepth) {

            if ((orderBookL2.getSide().equals(BID_TYPE) && orderType.equals(Order.OrderType.BID))
                    || (orderBookL2.getSide().equals(ASK_TYPE) && orderType.equals(Order.OrderType.ASK))) {

                LimitOrder limitOrder = new LimitOrder
                        .Builder(orderType, currencyPair)
                        .tradableAmount(orderBookL2.getSize())
                        .limitPrice(new BigDecimal(orderBookL2.getPrice()).setScale(scale, RoundingMode.HALF_UP))
                        .build();
                limitOrderList.add(limitOrder);
            }
        }

        return limitOrderList;
    }

    public static BigDecimal satoshiToBtc(BigDecimal amount) {
        BigDecimal satoshiInBtc = BigDecimal.valueOf(100000000);
        final int satoshiScale = 8;
        return amount != null
                ? amount.divide(satoshiInBtc, satoshiScale, BigDecimal.ROUND_HALF_UP)
                : BigDecimal.ZERO;
    }


    public static AccountInfoContracts adaptBitmexMargin(Margin marginInfo) {
        final BigDecimal wallet = marginInfo.getWalletBalance() != null ? satoshiToBtc(marginInfo.getWalletBalance()) : null;
        final BigDecimal available = marginInfo.getAvailableMargin() != null ? satoshiToBtc(marginInfo.getAvailableMargin()) : null;
        final BigDecimal eMark = marginInfo.getMarginBalance() != null ? satoshiToBtc(marginInfo.getMarginBalance()) : null;
        final BigDecimal upl = marginInfo.getUnrealisedPnl() != null ? satoshiToBtc(marginInfo.getUnrealisedPnl()) : null;
        final BigDecimal margin = (eMark != null && available != null)
                ? eMark.subtract(available)
                : null;
        return new AccountInfoContracts(
                wallet,
                available,
                eMark,
                null,
                null,
                null,
                margin,
                upl
        );
    }

    public static Balance adaptBitmexBalance(Wallet wallet) {
        return new Balance(new Currency(wallet.getCurrency()),
                satoshiToBtc(wallet.getAmount()),
                satoshiToBtc(wallet.getAmount()));
    }

    public static String adaptSymbol(CurrencyPair currencyPair) {
        return currencyPair.base.getSymbol().toUpperCase() + currencyPair.counter.getSymbol().toUpperCase();
    }

    public static org.knowm.xchange.dto.account.Position adaptBitmexPosition(Position position, String symbol) {
        if (position == null || position.getSymbol() == null || !position.getSymbol().equals(symbol)) {
            return new org.knowm.xchange.dto.account.Position(
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    "position is null"
            );
        }

        return new org.knowm.xchange.dto.account.Position(
                position.getCurrentQty(),
                BigDecimal.ZERO,
                position.getLeverage() != null ? BigDecimal.valueOf(position.getLeverage()) : BigDecimal.ZERO,
                position.getLiquidationPrice() != null ? BigDecimal.valueOf(position.getLiquidationPrice()) : BigDecimal.ZERO,
                position.getMarkValue(),
                position.getAvgEntryPrice() != null ? BigDecimal.valueOf(position.getAvgEntryPrice()) : BigDecimal.ZERO,
                position.getAvgEntryPrice() != null ? BigDecimal.valueOf(position.getAvgEntryPrice()) : BigDecimal.ZERO,
                position.toString()
        );
    }

    public static BigDecimal priceToBigDecimal(Double aDouble, Integer scale) {
        return new BigDecimal(aDouble).setScale(scale, RoundingMode.HALF_UP);
    }

    public static OpenOrders adaptOpenOrdersUpdate(JsonNode fullInputJson, Integer scale) throws JsonProcessingException {
        ObjectMapper mapper = new ObjectMapper();
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        mapper.registerModule(new JavaTimeModule());

        final ArrayList<LimitOrder> openOrders = new ArrayList<>();

        final JsonNode jsonNode = fullInputJson.get("data");
        if (jsonNode.getNodeType().equals(JsonNodeType.ARRAY)) {
            for (JsonNode node : jsonNode) {
                io.swagger.client.model.Order order = mapper.treeToValue(node, io.swagger.client.model.Order.class);

                //TODO MarketOrder can not be cast to LimitOrder
                final LimitOrder limitOrder = (LimitOrder) adaptOrder(order, true, scale);
                openOrders.add(limitOrder);
            }
        }

        return new OpenOrders(openOrders);
    }

    public static Order adaptOrder(io.swagger.client.model.Order order, Integer scale) {
        return adaptOrder(order, false, scale);
    }

    public static Order adaptOrder(io.swagger.client.model.Order order, boolean alwaysLimit, Integer scale) {
        final String side = order.getSide(); // may be null
        Order.OrderType orderType = null;
        BigDecimal tradableAmount = null;
        CurrencyPair currencyPair = null;
        BigDecimal price = null;
        BigDecimal avgPrice = null;

        if (side != null) {
            orderType = side.equals("Buy")
                    ? Order.OrderType.BID
                    : Order.OrderType.ASK;

        }
        if (order.getOrderQty() != null) {
            tradableAmount = order.getOrderQty();
        }
        if (order.getSymbol() != null) {
            final String first = order.getSymbol().substring(0, 3);
            final String second = order.getSymbol().substring(3);
            currencyPair = new CurrencyPair(new Currency(first), new Currency(second));
        }
        if (order.getPrice() != null) {
            price = priceToBigDecimal(order.getPrice(), scale);
        }
        if (order.getAvgPx() != null) {
            avgPrice = priceToBigDecimal(order.getAvgPx(), scale);
        }

        final Date timestamp = Date.from(order.getTimestamp().toInstant());

        final Order.OrderStatus orderStatus = convertOrderStatus(order.getOrdStatus());

        Order resultOrder;
        if (order.getOrdType() == null || order.getOrdType().equals("Limit") || alwaysLimit) {
            resultOrder = new LimitOrder(orderType,
                    tradableAmount,
                    currencyPair,
                    order.getOrderID(),
                    timestamp,
                    price,
                    avgPrice,
                    order.getCumQty(),
                    orderStatus);

        } else if (order.getOrdType().equals("Market")) {
            resultOrder = new MarketOrder(orderType,
                    tradableAmount,
                    currencyPair,
                    order.getOrderID(),
                    timestamp,
                    avgPrice,
                    order.getCumQty(),
                    orderStatus);
        } else {
            throw new IllegalStateException("unknown order type " + order.getOrdType());
        }
        return resultOrder;
    }

    private static Order.OrderStatus convertOrderStatus(String ordStatus) {
        if (ordStatus == null) {
            return null;
        }
        Order.OrderStatus orderStatus;
        if (ordStatus.toUpperCase().equals("PARTIALLYFILLED")) {
            orderStatus = Order.OrderStatus.PARTIALLY_FILLED;
        } else { //if (ordStatus.toUpperCase().equals("FILLED")) {
            //TODO check logs for "IllegalArgumentException: No enum constant org.knowm.xchange.dto.Order.OrderStatus."
            orderStatus = Order.OrderStatus.valueOf(ordStatus.toUpperCase());
        }

        return orderStatus;
    }

    public static LimitOrder updateLimitOrder(LimitOrder limitOrder, io.swagger.client.model.Order order, Integer scale) {
        final LimitOrder convertedOrd = (LimitOrder) BitmexAdapters.adaptOrder(order, true, scale);

        return new LimitOrder(
                limitOrder.getType(),
                limitOrder.getTradableAmount(),
                limitOrder.getCurrencyPair(),
                limitOrder.getId(),
                convertedOrd.getTimestamp() != null ? convertedOrd.getTimestamp() : limitOrder.getTimestamp(),
                convertedOrd.getLimitPrice() != null ? convertedOrd.getLimitPrice() : limitOrder.getLimitPrice(),
                convertedOrd.getAveragePrice() != null ? convertedOrd.getAveragePrice() : limitOrder.getAveragePrice(),
                convertedOrd.getCumulativeAmount() != null ? convertedOrd.getCumulativeAmount() : limitOrder.getCumulativeAmount(),
                convertedOrd.getStatus() != null ? convertedOrd.getStatus() : limitOrder.getStatus());
    }

}
