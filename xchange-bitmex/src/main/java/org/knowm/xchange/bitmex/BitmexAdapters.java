package org.knowm.xchange.bitmex;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeType;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import org.knowm.xchange.currency.Currency;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.dto.Order;
import org.knowm.xchange.dto.account.Balance;
import org.knowm.xchange.dto.marketdata.OrderBook;
import org.knowm.xchange.dto.trade.LimitOrder;
import org.knowm.xchange.dto.trade.OpenOrders;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import io.swagger.client.model.Margin;
import io.swagger.client.model.OrderBookL2;
import io.swagger.client.model.Position;
import io.swagger.client.model.Wallet;

/**
 * Created by Sergey Shurmin on 5/3/17.
 */
public class BitmexAdapters {
    /**
     * getTotal == Wallet Balance
     * getAvailable == Available Balance
     */
    public final static Currency WALLET_CURRENCY = new Currency("WALLET");
    /**
     * getTotal == Margin Balance
     * getAvailable == Margin Balance
     */
    public final static Currency MARGIN_CURRENCY = new Currency("MARGIN");

    private final static String BID_TYPE = "Buy";
    private final static String ASK_TYPE = "Sell";

    public static OrderBook adaptBitmexOrderBook(List<OrderBookL2> bitmexMarketDepth, CurrencyPair currencyPair) {
        List<LimitOrder> asks = adaptBitmexPublicOrders(bitmexMarketDepth, Order.OrderType.ASK, currencyPair);
        List<LimitOrder> bids = adaptBitmexPublicOrders(bitmexMarketDepth, Order.OrderType.BID, currencyPair);


        return new OrderBook(null, asks, bids);
    }

    private static List<LimitOrder> adaptBitmexPublicOrders(List<OrderBookL2> bitmexMarketDepth,
                                                            Order.OrderType orderType, CurrencyPair currencyPair) {
        List<LimitOrder> limitOrderList = new ArrayList<>();

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

    public static BigDecimal satoshiToBtc(BigDecimal amount) {
        BigDecimal satoshiInBtc = BigDecimal.valueOf(100000000);
        final int satoshiScale = 8;
        return amount != null
                ? amount.divide(satoshiInBtc, satoshiScale, BigDecimal.ROUND_HALF_UP)
                : BigDecimal.ZERO;
    }


    public static List<Balance> adaptBitmexMargin(Margin margin) {
        List<Balance> balanceList = new ArrayList<>();
        if (margin.getWalletBalance() != null) {
            balanceList.add(new Balance(WALLET_CURRENCY,
                    satoshiToBtc(margin.getWalletBalance()),
                    satoshiToBtc(margin.getAvailableMargin())
            ));
        }
        balanceList.add(new Balance(MARGIN_CURRENCY,
                satoshiToBtc(margin.getMarginBalance()),
                satoshiToBtc(margin.getMarginBalance())
        ));
        return balanceList;
    }

    public static Balance adaptBitmexBalance(Wallet wallet) {
        return new Balance(new Currency(wallet.getCurrency()),
                satoshiToBtc(wallet.getAmount()),
                satoshiToBtc(wallet.getAmount()));
    }

    public static String adaptSymbol(CurrencyPair currencyPair) {
        return currencyPair.base.getSymbol().toUpperCase() + currencyPair.counter.getSymbol().toUpperCase();
    }

    public static org.knowm.xchange.dto.account.Position adaptBitmexPosition(Position position) {
        return new org.knowm.xchange.dto.account.Position(
                position.getCurrentQty(),
                BigDecimal.ZERO,
                BigDecimal.valueOf(position.getLeverage()),
                position.toString()
        );
    }

    public static BigDecimal priceToBigDecimal(Double aDouble) {
        return new BigDecimal(aDouble)
                .setScale(1, RoundingMode.HALF_UP);
    }

    public static OpenOrders adaptOpenOrdersUpdate(JsonNode fullInputJson) throws JsonProcessingException {
        ObjectMapper mapper = new ObjectMapper();
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        mapper.registerModule(new JavaTimeModule());

        final ArrayList<LimitOrder> openOrders = new ArrayList<>();

        final JsonNode jsonNode = fullInputJson.get("data");
        if (jsonNode.getNodeType().equals(JsonNodeType.ARRAY)) {
            for (JsonNode node : jsonNode) {
                io.swagger.client.model.Order order = mapper.treeToValue(node, io.swagger.client.model.Order.class);

                final String side = order.getSide(); // may be null
                org.knowm.xchange.dto.Order.OrderType orderType = null;
                BigDecimal tradableAmount = null;
                CurrencyPair currencyPair = null;
                BigDecimal price = null;

                if (side != null) {
                    orderType = side.equals("Buy")
                            ? org.knowm.xchange.dto.Order.OrderType.BID
                            : org.knowm.xchange.dto.Order.OrderType.ASK;

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
                    price = priceToBigDecimal(order.getPrice());
                }

                final Date timestamp = Date.from(order.getTimestamp().toInstant());

                openOrders.add(
                        new LimitOrder(orderType,
                                tradableAmount,
                                currencyPair,
                                order.getOrderID(),
                                timestamp,
                                price)
                );
            }
        }

        return new OpenOrders(openOrders);
    }

}
