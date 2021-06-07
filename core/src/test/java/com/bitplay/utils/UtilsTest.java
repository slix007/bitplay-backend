package com.bitplay.utils;

import com.bitplay.market.bitmex.BitmexService;
import com.bitplay.persistance.domain.settings.PlacingBlocks;
import org.junit.Test;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.dto.Order;
import org.knowm.xchange.dto.marketdata.OrderBook;
import org.knowm.xchange.dto.trade.LimitOrder;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;

import static org.junit.Assert.*;

/**
 * Created by Sergey Shurmin on 11/8/17.
 */
public class UtilsTest {

    @Test
    public void getAvgPrice() {

        final ArrayList<LimitOrder> asks = new ArrayList<>();
        asks.add(new LimitOrder(Order.OrderType.ASK, new BigDecimal("10"), CurrencyPair.BTC_USD, "", null, new BigDecimal("1000.00")));
        asks.add(new LimitOrder(Order.OrderType.ASK, new BigDecimal("12"), CurrencyPair.BTC_USD, "", null, new BigDecimal("1000.45")));
        asks.add(new LimitOrder(Order.OrderType.ASK, new BigDecimal("1"), CurrencyPair.BTC_USD, "", null, new BigDecimal("1001.00")));
        final ArrayList<LimitOrder> bids = new ArrayList<>();
        bids.add(new LimitOrder(Order.OrderType.BID, new BigDecimal("2"), CurrencyPair.BTC_USD, "", null, new BigDecimal("999.45")));
        bids.add(new LimitOrder(Order.OrderType.BID, new BigDecimal("5"), CurrencyPair.BTC_USD, "", null, new BigDecimal("999.35")));
        bids.add(new LimitOrder(Order.OrderType.BID, new BigDecimal("10"), CurrencyPair.BTC_USD, "", null, new BigDecimal("999.00")));

        final BigDecimal avgPrice = Utils.getAvgPrice(new OrderBook(new Date(), asks, bids), 0, 11);
        assertEquals("avgPrice", BigDecimal.valueOf(1000.04), avgPrice);

        final BigDecimal avgPrice2 = Utils.getAvgPrice(new OrderBook(new Date(), asks, bids), 10, 0);
        assertEquals("avgPrice", BigDecimal.valueOf(999.27), avgPrice2);

        final BigDecimal avgPrice3 = Utils.getAvgPrice(new OrderBook(new Date(), asks, bids), 10, 2);
        assertEquals("avgPrice", BigDecimal.valueOf(999.39), avgPrice3);

        final BigDecimal avgPrice4 = Utils.getAvgPrice(new OrderBook(new Date(), asks, bids), 0, 0);
        assertEquals("avgPrice", BigDecimal.valueOf(0), avgPrice4);
    }

    @Test
    public void testBigDec() {

//        final BigDecimal v = BigDecimal.valueOf(299);
//        final BigDecimal remainder = v.remainder(BitmexService.LOT_SIZE);
//        final BigDecimal res = v.subtract(remainder);

        System.out.println(PlacingBlocks.scaleBitmexCont(BigDecimal.valueOf(299)));
        System.out.println(PlacingBlocks.scaleBitmexCont(BigDecimal.valueOf(250)));
        System.out.println(PlacingBlocks.scaleBitmexCont(BigDecimal.valueOf(249)));
        System.out.println(PlacingBlocks.scaleBitmexCont(BigDecimal.valueOf(1920)));
        System.out.println(PlacingBlocks.scaleBitmexCont(BigDecimal.valueOf(1900)));
        System.out.println(PlacingBlocks.scaleBitmexCont(BigDecimal.valueOf(Integer.MAX_VALUE)));

//        System.out.println(res);
//        System.out.println(remainder);
//        System.out.println(remainder);

    }

}