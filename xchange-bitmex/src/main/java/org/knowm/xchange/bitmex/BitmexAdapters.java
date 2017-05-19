package org.knowm.xchange.bitmex;

import org.knowm.xchange.currency.Currency;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.dto.Order;
import org.knowm.xchange.dto.account.Balance;
import org.knowm.xchange.dto.marketdata.OrderBook;
import org.knowm.xchange.dto.trade.LimitOrder;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

import io.swagger.client.model.Margin;
import io.swagger.client.model.OrderBookL2;
import io.swagger.client.model.Position;
import io.swagger.client.model.Wallet;

/**
 * Created by Sergey Shurmin on 5/3/17.
 */
public class BitmexAdapters {
    private final static String BID_TYPE = "Buy";
    private final static String ASK_TYPE = "Sell";

    public static OrderBook adaptBitmexOrderBook(List<OrderBookL2> bitmexMarketDepth, CurrencyPair currencyPair) {
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
                        .tradableAmount(satoshiToBtc(orderBookL2.getSize()))
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


    public static Balance adaptBitmexMargin(Margin margin) {
        return new Balance(new Currency("MARGIN"),
                satoshiToBtc(margin.getWalletBalance()),
                satoshiToBtc(margin.getAvailableMargin()),
                BigDecimal.ZERO,
                satoshiToBtc(margin.getMarginBalance()),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO
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

    public static Balance adaptBitmexPosition(Position position) {
        return new Balance(new Currency("POSITION"),
                position.getCurrentQty(),
                new BigDecimal(position.getSimpleQty()).setScale(4, BigDecimal.ROUND_HALF_UP),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO
        );
    }
}
