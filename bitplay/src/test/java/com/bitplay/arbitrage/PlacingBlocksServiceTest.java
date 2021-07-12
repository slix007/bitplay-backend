package com.bitplay.arbitrage;

import com.bitplay.arbitrage.dto.PlBlocks;
import com.bitplay.xchange.currency.CurrencyPair;
import com.bitplay.xchange.dto.Order;
import com.bitplay.xchange.dto.Order.OrderType;
import com.bitplay.xchange.dto.marketdata.OrderBook;
import com.bitplay.xchange.dto.trade.LimitOrder;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

/**
 * Created by Sergey Shurmin on 1/7/18.
 */
public class PlacingBlocksServiceTest {

    final static BigDecimal BITMEX_BTC_FACTOR = BigDecimal.valueOf(100);

    PlacingBlocksService placingBlocksService = new PlacingBlocksService();

    private OrderBook oOb;
    private OrderBook bOb;

    private void initOrderBooks() {
        final OrderType ask = Order.OrderType.ASK;
        final Order.OrderType bid = Order.OrderType.BID;
        final ArrayList<LimitOrder> oAsks = new ArrayList<>();
        oAsks.add(new LimitOrder(ask, BigDecimal.valueOf(2), CurrencyPair.BTC_USD, "17", new Date(), BigDecimal.valueOf(16908.89)));
        oAsks.add(new LimitOrder(ask, BigDecimal.valueOf(2), CurrencyPair.BTC_USD, "57", new Date(), BigDecimal.valueOf(16908.9)));
        oAsks.add(new LimitOrder(ask, BigDecimal.valueOf(1), CurrencyPair.BTC_USD, "68", new Date(), BigDecimal.valueOf(16908.91)));
        oAsks.add(new LimitOrder(ask, BigDecimal.valueOf(1), CurrencyPair.BTC_USD, "47", new Date(), BigDecimal.valueOf(16908.93)));
        oAsks.add(new LimitOrder(ask, BigDecimal.valueOf(1), CurrencyPair.BTC_USD, "78", new Date(), BigDecimal.valueOf(16908.94)));
        oAsks.add(new LimitOrder(ask, BigDecimal.valueOf(1), CurrencyPair.BTC_USD, "88", new Date(), BigDecimal.valueOf(16908.96)));
        oAsks.add(new LimitOrder(ask, BigDecimal.valueOf(1), CurrencyPair.BTC_USD, "67", new Date(), BigDecimal.valueOf(16908.97)));
        oAsks.add(new LimitOrder(ask, BigDecimal.valueOf(6), CurrencyPair.BTC_USD, "36", new Date(), BigDecimal.valueOf(16910)));
        oAsks.add(new LimitOrder(ask, BigDecimal.valueOf(24), CurrencyPair.BTC_USD, "9", new Date(), BigDecimal.valueOf(16911.45)));
        oAsks.add(new LimitOrder(ask, BigDecimal.valueOf(25), CurrencyPair.BTC_USD, "2", new Date(), BigDecimal.valueOf(16914.28)));
        final ArrayList<LimitOrder> oBids = new ArrayList<>();
        oBids.add(new LimitOrder(bid, BigDecimal.valueOf(6), CurrencyPair.BTC_USD, "156", new Date(), BigDecimal.valueOf(16886.43)));
        oBids.add(new LimitOrder(bid, BigDecimal.valueOf(1), CurrencyPair.BTC_USD, "145", new Date(), BigDecimal.valueOf(16886.41)));
        oBids.add(new LimitOrder(bid, BigDecimal.valueOf(11), CurrencyPair.BTC_USD, "13", new Date(), BigDecimal.valueOf(16884.15)));
        oBids.add(new LimitOrder(bid, BigDecimal.valueOf(10), CurrencyPair.BTC_USD, "12", new Date(), BigDecimal.valueOf(16879.59)));
        oBids.add(new LimitOrder(bid, BigDecimal.valueOf(10), CurrencyPair.BTC_USD, "11", new Date(), BigDecimal.valueOf(16879.58)));
        oBids.add(new LimitOrder(bid, BigDecimal.valueOf(6), CurrencyPair.BTC_USD, "173", new Date(), BigDecimal.valueOf(16879.57)));
        oBids.add(new LimitOrder(bid, BigDecimal.valueOf(80), CurrencyPair.BTC_USD, "18", new Date(), BigDecimal.valueOf(16879.46)));
        oBids.add(new LimitOrder(bid, BigDecimal.valueOf(13), CurrencyPair.BTC_USD, "19", new Date(), BigDecimal.valueOf(16878.74)));
        oBids.add(new LimitOrder(bid, BigDecimal.valueOf(20), CurrencyPair.BTC_USD, "10", new Date(), BigDecimal.valueOf(16878.72)));
        oBids.add(new LimitOrder(bid, BigDecimal.valueOf(30), CurrencyPair.BTC_USD, "16", new Date(), BigDecimal.valueOf(16877.41)));
        oOb = new OrderBook(new Date(), oAsks, oBids);

        final ArrayList<LimitOrder> bAsks = new ArrayList<>();
        bAsks.add(new LimitOrder(ask, BigDecimal.valueOf(342), CurrencyPair.BTC_USD, "adf1f", new Date(), BigDecimal.valueOf(16471)));
        bAsks.add(new LimitOrder(ask, BigDecimal.valueOf(98815), CurrencyPair.BTC_USD, "a12", new Date(), BigDecimal.valueOf(16471.5)));
        bAsks.add(new LimitOrder(ask, BigDecimal.valueOf(10000), CurrencyPair.BTC_USD, "a31", new Date(), BigDecimal.valueOf(16472)));
        bAsks.add(new LimitOrder(ask, BigDecimal.valueOf(3850), CurrencyPair.BTC_USD, "a4g1", new Date(), BigDecimal.valueOf(16472.5)));
        bAsks.add(new LimitOrder(ask, BigDecimal.valueOf(600), CurrencyPair.BTC_USD, "afg15", new Date(), BigDecimal.valueOf(16473)));
        bAsks.add(new LimitOrder(ask, BigDecimal.valueOf(12150), CurrencyPair.BTC_USD, "a14", new Date(), BigDecimal.valueOf(16473.5)));
        bAsks.add(new LimitOrder(ask, BigDecimal.valueOf(1158), CurrencyPair.BTC_USD, "a61g", new Date(), BigDecimal.valueOf(16474)));
        bAsks.add(new LimitOrder(ask, BigDecimal.valueOf(50), CurrencyPair.BTC_USD, "a1ddg7", new Date(), BigDecimal.valueOf(16474.5)));
        bAsks.add(new LimitOrder(ask, BigDecimal.valueOf(50), CurrencyPair.BTC_USD, "a81dgf", new Date(), BigDecimal.valueOf(16475)));
        bAsks.add(new LimitOrder(ask, BigDecimal.valueOf(9293), CurrencyPair.BTC_USD, "a1d9", new Date(), BigDecimal.valueOf(16475.5)));
        final ArrayList<LimitOrder> bBids = new ArrayList<>();
        bBids.add(new LimitOrder(ask, BigDecimal.valueOf(8583), CurrencyPair.BTC_USD, "ba1", new Date(), BigDecimal.valueOf(16470.5)));
        bBids.add(new LimitOrder(ask, BigDecimal.valueOf(1200), CurrencyPair.BTC_USD, "ca1", new Date(), BigDecimal.valueOf(16470)));
        bBids.add(new LimitOrder(ask, BigDecimal.valueOf(922), CurrencyPair.BTC_USD, "sfa1", new Date(), BigDecimal.valueOf(16469)));
        bBids.add(new LimitOrder(ask, BigDecimal.valueOf(400), CurrencyPair.BTC_USD, "daf1", new Date(), BigDecimal.valueOf(16468.5)));
        bBids.add(new LimitOrder(ask, BigDecimal.valueOf(2000), CurrencyPair.BTC_USD, "fa1", new Date(), BigDecimal.valueOf(16465.5)));
        bBids.add(new LimitOrder(ask, BigDecimal.valueOf(1258), CurrencyPair.BTC_USD, "ga1", new Date(), BigDecimal.valueOf(16464.5)));
        bBids.add(new LimitOrder(ask, BigDecimal.valueOf(1239), CurrencyPair.BTC_USD, "ja1", new Date(), BigDecimal.valueOf(16464)));
        bBids.add(new LimitOrder(ask, BigDecimal.valueOf(5810), CurrencyPair.BTC_USD, "ka1", new Date(), BigDecimal.valueOf(16463.5)));
        bBids.add(new LimitOrder(ask, BigDecimal.valueOf(20000), CurrencyPair.BTC_USD, "t1", new Date(), BigDecimal.valueOf(16461.5)));
        bBids.add(new LimitOrder(ask, BigDecimal.valueOf(464), CurrencyPair.BTC_USD, "ea1h", new Date(), BigDecimal.valueOf(16460)));
        bOb = new OrderBook(new Date(), bAsks, bBids);
    }

    @Before
    public void setUp() {
        initOrderBooks();
    }

    @Test
    public void getDynamicBlockBitmexInsideFirst() {
        // b_delta=-438.39
        final BigDecimal oPL = BigDecimal.ZERO;
        final BigDecimal oPS = BigDecimal.ZERO;
        final BigDecimal maxBlock = BigDecimal.valueOf(100); // okex_am[0]
        final BigDecimal bBorder = BigDecimal.valueOf(-438.39);
        final PlBlocks dynamicBlockBitmex = placingBlocksService.getDynamicBlockByBDelta(bOb, oOb, bBorder, maxBlock, BITMEX_BTC_FACTOR);
        Assert.assertEquals(BigDecimal.valueOf(1), dynamicBlockBitmex.getBlockOkex());
    }

    @Test
    public void getDynamicBlockBitmexWholeFirst() {
        // b_delta=-438.39
        final BigDecimal oPL = BigDecimal.ZERO;
        final BigDecimal oPS = BigDecimal.ZERO;
        final BigDecimal maxBlock = BigDecimal.valueOf(500); // okex_am[0]
        final BigDecimal bBorder = BigDecimal.valueOf(-438.39);
        final PlBlocks dynamicBlockBitmex = placingBlocksService.getDynamicBlockByBDelta(bOb, oOb, bBorder, maxBlock, BITMEX_BTC_FACTOR);
        Assert.assertEquals(BigDecimal.valueOf(2), dynamicBlockBitmex.getBlockOkex());
    }

    @Test
    public void getDynamicBlockBitmexSecond() {
        // b_delta=-438.39
        // b_delta=-438.4  // b_bid[0] - o_ask[1]
        final BigDecimal oPL = BigDecimal.ZERO;
        final BigDecimal oPS = BigDecimal.ZERO;
        final BigDecimal maxBlock = BigDecimal.valueOf(500);
        final BigDecimal bBorder = BigDecimal.valueOf(-438.4);
        final PlBlocks dynamicBlockBitmex = placingBlocksService.getDynamicBlockByBDelta(bOb, oOb, bBorder, maxBlock, BITMEX_BTC_FACTOR);
        Assert.assertEquals(BigDecimal.valueOf(4), dynamicBlockBitmex.getBlockOkex());
    }

    @Test
    public void getDynamicBlockOkexFirstFull() {
        // o_delta=415.43
        final BigDecimal oPL = BigDecimal.ZERO;
        final BigDecimal oPS = BigDecimal.ZERO;
        final BigDecimal maxBlock = BigDecimal.valueOf(500);
        final BigDecimal oBorder = BigDecimal.valueOf(415.43);
        final PlBlocks dynamicBlockBitmex = placingBlocksService.getDynamicBlockByODelta(bOb, oOb, oBorder, maxBlock, BITMEX_BTC_FACTOR);
        Assert.assertEquals(BigDecimal.valueOf(3), dynamicBlockBitmex.getBlockOkex());
    }

    @Test
    public void getDynamicBlockOkexFirstPart() {
        // o_delta=415.43
        final BigDecimal oPL = BigDecimal.ZERO;
        final BigDecimal oPS = BigDecimal.ZERO;
        final BigDecimal maxBlock = BigDecimal.valueOf(200); // bitmex am[0]=342
        final BigDecimal oBorder = BigDecimal.valueOf(415.43);
        final PlBlocks dynamicBlockBitmex = placingBlocksService.getDynamicBlockByODelta(bOb, oOb, oBorder, maxBlock, BITMEX_BTC_FACTOR);
        Assert.assertEquals(BigDecimal.valueOf(2), dynamicBlockBitmex.getBlockOkex());
    }

    @Test
    public void getDynamicBlockOkexSecondOne() {
        // o_delta=415.43
        // o_delta[b2,o1] = 414.93
        final BigDecimal oPL = BigDecimal.ZERO;
        final BigDecimal oPS = BigDecimal.ZERO;
        final BigDecimal maxBlock = BigDecimal.valueOf(500); // bitmex am[0]=342
        final BigDecimal oBorder = BigDecimal.valueOf(414.93);
        final PlBlocks dynamicBlockBitmex = placingBlocksService.getDynamicBlockByODelta(bOb, oOb, oBorder, maxBlock, BITMEX_BTC_FACTOR);
        Assert.assertEquals(BigDecimal.valueOf(5), dynamicBlockBitmex.getBlockOkex());
    }

    @Test
    public void getDynamicBlockOkexSecondBoth() {
        // o_delta=415.43
        // o_delta[b2,o1] = 414.93
        // o_delta[b2,o2] = 414.91
        final BigDecimal oPL = BigDecimal.ZERO;
        final BigDecimal oPS = BigDecimal.ZERO;
        final BigDecimal maxBlock = BigDecimal.valueOf(1000);
        final BigDecimal oBorder = BigDecimal.valueOf(414.91);
        final PlBlocks dynamicBlockBitmex = placingBlocksService.getDynamicBlockByODelta(bOb, oOb, oBorder, maxBlock, BITMEX_BTC_FACTOR);
        Assert.assertEquals(BigDecimal.valueOf(7), dynamicBlockBitmex.getBlockOkex());
    }

    @Test
    public void getDynamicBlockOkexThirdAndSecond() {
        // o_delta=415.43
        // o_delta[b2,o1] = 414.93
        // o_delta[b2,o2] = 414.91
        // o_delta[b3,o2] = 412.65
        // o_delta[b3,o2] = 408.09
        final BigDecimal oPL = BigDecimal.ZERO;
        final BigDecimal oPS = BigDecimal.ZERO;
        final BigDecimal maxBlock = BigDecimal.valueOf(2500);
        final BigDecimal oBorder = BigDecimal.valueOf(408.10); // 408.09 < 408.10 < 412.65
        final PlBlocks dynamicBlockBitmex = placingBlocksService.getDynamicBlockByODelta(bOb, oOb, oBorder, maxBlock, BITMEX_BTC_FACTOR);
        Assert.assertEquals(BigDecimal.valueOf(18), dynamicBlockBitmex.getBlockOkex());
    }

    // ---------------- OKEX position bounds -------------------

    @Test
    public void getDynamicBlockOkexFirstFullPlusPosBound() {
        // o_delta=415.43
        final BigDecimal oPL = BigDecimal.valueOf(2);
        final BigDecimal oPS = BigDecimal.ZERO;
        final BigDecimal maxBlock = BigDecimal.valueOf(500);
        final BigDecimal oBorder = BigDecimal.valueOf(415.43);
        // DELTA2_B_BUY_O_SELL
        PlBlocks dynamicBlockBitmex = placingBlocksService.getDynamicBlockByODelta(bOb, oOb, oBorder, maxBlock, BITMEX_BTC_FACTOR);
        dynamicBlockBitmex = placingBlocksService.minByPos(dynamicBlockBitmex, oPL, BITMEX_BTC_FACTOR);
        // Unbound == 3
        Assert.assertEquals(BigDecimal.valueOf(2), dynamicBlockBitmex.getBlockOkex());
    }

    @Test
    public void getDynamicBlockBitmexSecondPlusPosBound() {
        // b_delta=-438.39
        // b_delta=-438.4  // b_bid[0] - o_ask[1]
        final BigDecimal oPL = BigDecimal.ZERO;
        final BigDecimal oPS = BigDecimal.valueOf(3);
        final BigDecimal maxBlock = BigDecimal.valueOf(500);
        final BigDecimal bBorder = BigDecimal.valueOf(-438.4);
        PlBlocks dynamicBlockBitmex = placingBlocksService.getDynamicBlockByBDelta(bOb, oOb, bBorder, maxBlock, BITMEX_BTC_FACTOR);
        dynamicBlockBitmex = placingBlocksService.minByPos(dynamicBlockBitmex, oPS, BITMEX_BTC_FACTOR);
        // Unbound == 4
        Assert.assertEquals(BigDecimal.valueOf(3), dynamicBlockBitmex.getBlockOkex());
    }

    @Test
    public void getDynamicBlockBitmexSecondPlusPosInsideBound() {
        // b_delta=-438.39
        // b_delta=-438.4  // b_bid[0] - o_ask[1]
        final BigDecimal oPL = BigDecimal.ZERO;
        final BigDecimal oPS = BigDecimal.valueOf(10);
        final BigDecimal maxBlock = BigDecimal.valueOf(500);
        final BigDecimal bBorder = BigDecimal.valueOf(-438.4);
        final PlBlocks dynamicBlockBitmex = placingBlocksService.getDynamicBlockByBDelta(bOb, oOb, bBorder, maxBlock, BITMEX_BTC_FACTOR);
        // Unbound == 4
        Assert.assertEquals(BigDecimal.valueOf(4), dynamicBlockBitmex.getBlockOkex());
    }

}
