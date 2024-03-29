package com.bitplay.arbitrage;

import com.bitplay.arbitrage.BordersService.TradeType;
import com.bitplay.arbitrage.dto.DiffFactBr;
import com.bitplay.market.bitmex.BitmexService;
import com.bitplay.market.model.Affordable;
import com.bitplay.persistance.PersistenceService;
import com.bitplay.persistance.SettingsRepositoryService;
import com.bitplay.persistance.domain.borders.BorderItem;
import com.bitplay.persistance.domain.borders.BorderParams;
import com.bitplay.persistance.domain.borders.BorderTable;
import com.bitplay.persistance.domain.borders.BordersV1;
import com.bitplay.persistance.domain.borders.BordersV2;
import com.bitplay.persistance.domain.settings.PlacingBlocks;
import com.bitplay.persistance.domain.settings.PlacingBlocks.Ver;
import com.bitplay.persistance.domain.settings.Settings;
import com.bitplay.model.Pos;
import com.bitplay.xchange.currency.CurrencyPair;
import com.bitplay.xchange.dto.Order;
import com.bitplay.xchange.dto.Order.OrderType;
import com.bitplay.xchange.dto.marketdata.OrderBook;
import com.bitplay.xchange.dto.trade.LimitOrder;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.runners.MockitoJUnitRunner;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import static com.bitplay.arbitrage.TestingMocks.toUsd;
import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.when;

/**
 * Created by Sergey Shurmin on 1/9/18.
 */
@RunWith(MockitoJUnitRunner.class)
public class BordersServiceTest {

    @InjectMocks
    BordersService bordersService = new BordersService();

    final Settings settings = new Settings();
    @Mock
    PersistenceService persistenceService;
    @Mock
    SettingsRepositoryService settingsRepositoryService;
    @Mock
    ArbitrageService arbitrageService;
    @Spy
    PlacingBlocksService placingBlocksService = new PlacingBlocksService();
    BorderParams borderParams;
    private OrderBook oOb;
    private OrderBook bOb;

    private void initOrderBooks() {
        final OrderType ask = Order.OrderType.ASK;
        final Order.OrderType bid = Order.OrderType.BID;

        final ArrayList<LimitOrder> bAsks = new ArrayList<>();
        bAsks.add(new LimitOrder(ask, BigDecimal.valueOf(342), CurrencyPair.BTC_USD, "adf1f", new Date(), BigDecimal.valueOf(16834)));
        bAsks.add(new LimitOrder(ask, BigDecimal.valueOf(98815), CurrencyPair.BTC_USD, "a12", new Date(), BigDecimal.valueOf(16834.5)));
        bAsks.add(new LimitOrder(ask, BigDecimal.valueOf(10000), CurrencyPair.BTC_USD, "a31", new Date(), BigDecimal.valueOf(16835)));
        bAsks.add(new LimitOrder(ask, BigDecimal.valueOf(3850), CurrencyPair.BTC_USD, "a4g1", new Date(), BigDecimal.valueOf(16835.5)));
        bAsks.add(new LimitOrder(ask, BigDecimal.valueOf(600), CurrencyPair.BTC_USD, "afg15", new Date(), BigDecimal.valueOf(16836)));
        bAsks.add(new LimitOrder(ask, BigDecimal.valueOf(12150), CurrencyPair.BTC_USD, "a14", new Date(), BigDecimal.valueOf(16836.5)));
        bAsks.add(new LimitOrder(ask, BigDecimal.valueOf(1158), CurrencyPair.BTC_USD, "a61g", new Date(), BigDecimal.valueOf(16837)));
        bAsks.add(new LimitOrder(ask, BigDecimal.valueOf(50), CurrencyPair.BTC_USD, "a1ddg7", new Date(), BigDecimal.valueOf(16837.5)));
        bAsks.add(new LimitOrder(ask, BigDecimal.valueOf(50), CurrencyPair.BTC_USD, "a81dgf", new Date(), BigDecimal.valueOf(16838)));
        bAsks.add(new LimitOrder(ask, BigDecimal.valueOf(9293), CurrencyPair.BTC_USD, "a1d9", new Date(), BigDecimal.valueOf(16838.5)));
        final ArrayList<LimitOrder> bBids = new ArrayList<>();
        bBids.add(new LimitOrder(ask, BigDecimal.valueOf(8583), CurrencyPair.BTC_USD, "ba1", new Date(), BigDecimal.valueOf(16939))); // 1
        bBids.add(new LimitOrder(ask, BigDecimal.valueOf(1200), CurrencyPair.BTC_USD, "ca1", new Date(), BigDecimal.valueOf(16832)));
        bBids.add(new LimitOrder(ask, BigDecimal.valueOf(922), CurrencyPair.BTC_USD, "sfa1", new Date(), BigDecimal.valueOf(16831)));
        bBids.add(new LimitOrder(ask, BigDecimal.valueOf(400), CurrencyPair.BTC_USD, "daf1", new Date(), BigDecimal.valueOf(16830.5)));
        bBids.add(new LimitOrder(ask, BigDecimal.valueOf(2000), CurrencyPair.BTC_USD, "fa1", new Date(), BigDecimal.valueOf(16829.5)));
        bBids.add(new LimitOrder(ask, BigDecimal.valueOf(1258), CurrencyPair.BTC_USD, "ga1", new Date(), BigDecimal.valueOf(16828.5)));
        bBids.add(new LimitOrder(ask, BigDecimal.valueOf(1239), CurrencyPair.BTC_USD, "ja1", new Date(), BigDecimal.valueOf(16827)));
        bBids.add(new LimitOrder(ask, BigDecimal.valueOf(5810), CurrencyPair.BTC_USD, "ka1", new Date(), BigDecimal.valueOf(16826.5)));
        bBids.add(new LimitOrder(ask, BigDecimal.valueOf(20000), CurrencyPair.BTC_USD, "t1", new Date(), BigDecimal.valueOf(16825.5)));
        bBids.add(new LimitOrder(ask, BigDecimal.valueOf(464), CurrencyPair.BTC_USD, "ea1h", new Date(), BigDecimal.valueOf(16824)));
        bOb = new OrderBook(new Date(), bAsks, bBids);

        final ArrayList<LimitOrder> oAsks = new ArrayList<>();
        oAsks.add(new LimitOrder(ask, BigDecimal.valueOf(2), CurrencyPair.BTC_USD, "17", new Date(), BigDecimal.valueOf(16887)));  // 1
        oAsks.add(new LimitOrder(ask, BigDecimal.valueOf(2), CurrencyPair.BTC_USD, "57", new Date(), BigDecimal.valueOf(16897.9)));
        oAsks.add(new LimitOrder(ask, BigDecimal.valueOf(1), CurrencyPair.BTC_USD, "68", new Date(), BigDecimal.valueOf(16897.91)));
        oAsks.add(new LimitOrder(ask, BigDecimal.valueOf(1), CurrencyPair.BTC_USD, "47", new Date(), BigDecimal.valueOf(16897.93)));
        oAsks.add(new LimitOrder(ask, BigDecimal.valueOf(1), CurrencyPair.BTC_USD, "78", new Date(), BigDecimal.valueOf(16897.94)));
        oAsks.add(new LimitOrder(ask, BigDecimal.valueOf(1), CurrencyPair.BTC_USD, "88", new Date(), BigDecimal.valueOf(16897.96)));
        oAsks.add(new LimitOrder(ask, BigDecimal.valueOf(1), CurrencyPair.BTC_USD, "67", new Date(), BigDecimal.valueOf(16897.97)));
        oAsks.add(new LimitOrder(ask, BigDecimal.valueOf(6), CurrencyPair.BTC_USD, "36", new Date(), BigDecimal.valueOf(16899)));
        oAsks.add(new LimitOrder(ask, BigDecimal.valueOf(24), CurrencyPair.BTC_USD, "9", new Date(), BigDecimal.valueOf(16900.45)));
        oAsks.add(new LimitOrder(ask, BigDecimal.valueOf(25), CurrencyPair.BTC_USD, "2", new Date(), BigDecimal.valueOf(16901.28)));
        final ArrayList<LimitOrder> oBids = new ArrayList<>();
        oBids.add(new LimitOrder(bid, BigDecimal.valueOf(6), CurrencyPair.BTC_USD, "156", new Date(), BigDecimal.valueOf(16886)));
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

    }

    private BorderParams createDefaultBorders() {
        final List<BorderTable> borders = new ArrayList<>();
        final List<BorderItem> borderBtmClose = new ArrayList<>();
        borderBtmClose.add(new BorderItem(1, BigDecimal.valueOf(70), 0, 0));
        borderBtmClose.add(new BorderItem(2, BigDecimal.valueOf(60), 500, 500));
        borderBtmClose.add(new BorderItem(3, BigDecimal.valueOf(50), 1000, 1000));
        borderBtmClose.add(new BorderItem(4, BigDecimal.valueOf(40), 1500, 1500));
        borders.add(new BorderTable("b_br_close", borderBtmClose));
        final List<BorderItem> borderBtmOpen = new ArrayList<>();
        borderBtmOpen.add(new BorderItem(1, BigDecimal.valueOf(20), 500, 500));
        borderBtmOpen.add(new BorderItem(2, BigDecimal.valueOf(30), 1000, 1000));
        borderBtmOpen.add(new BorderItem(3, BigDecimal.valueOf(40), 1500, 1500));
        borderBtmOpen.add(new BorderItem(4, BigDecimal.valueOf(50), 2000, 2000));
        borderBtmOpen.add(new BorderItem(5, BigDecimal.valueOf(60), 2500, 2500));
        borders.add(new BorderTable("b_br_open", borderBtmOpen));
        final List<BorderItem> borderOkexClose = new ArrayList<>();
        borderOkexClose.add(new BorderItem(1, BigDecimal.valueOf(160), 0, 0));
        borderOkexClose.add(new BorderItem(2, BigDecimal.valueOf(150), 500, 500));
        borderOkexClose.add(new BorderItem(3, BigDecimal.valueOf(140), 1000, 1000));
        borderOkexClose.add(new BorderItem(4, BigDecimal.valueOf(130), 1500, 1500));
        borders.add(new BorderTable("o_br_close", borderOkexClose));
        final List<BorderItem> borderOkexOpen = new ArrayList<>();
        borderOkexOpen.add(new BorderItem(1, BigDecimal.valueOf(170), 500, 500));
        borderOkexOpen.add(new BorderItem(2, BigDecimal.valueOf(180), 1000, 1000));
        borderOkexOpen.add(new BorderItem(3, BigDecimal.valueOf(190), 1500, 1500));
        borderOkexOpen.add(new BorderItem(4, BigDecimal.valueOf(200), 2000, 2000));
        borders.add(new BorderTable("o_br_open", borderOkexOpen));

        return TestingMocks.toUsd(new BorderParams(BorderParams.Ver.V2, new BordersV1(), new BordersV2(borders)));
    }


    @Before
    public void init() {
        initOrderBooks();

        final PlacingBlocks placingBlocks = new PlacingBlocks();
        placingBlocks.setActiveVersion(PlacingBlocks.Ver.FIXED);
        placingBlocks.setFixedBlockUsd(BigDecimal.valueOf(100));
        settings.setPlacingBlocks(placingBlocks);

        when(settingsRepositoryService.getSettings()).thenReturn(settings);
        borderParams = createDefaultBorders();

        when(persistenceService.fetchBorders()).thenReturn(borderParams);

        when(persistenceService.getSettingsRepositoryService()).thenReturn(settingsRepositoryService);

        when(arbitrageService.isEth()).thenReturn(false);
        when(arbitrageService.getCm()).thenReturn(BigDecimal.valueOf(100));
        final BitmexService bitmexService = new BitmexService();
        when(arbitrageService.getLeftMarketService()).thenReturn(bitmexService);
    }

    /** like Ex.2 */
    @Test
    public void test_Fixed_okMode() {
//        B_delta: +52
//        O_delta: -54
        borderParams.setPosMode(BorderParams.PosMode.RIGHT_MODE);
        settings.getPlacingBlocks().setActiveVersion(PlacingBlocks.Ver.FIXED);
        settings.getPlacingBlocks().setFixedBlockUsd(BigDecimal.valueOf(200 * 100));

        // delta1 == // b_bid[0] - o_ask[1]
        final BigDecimal delta1 = BigDecimal.valueOf(52);
        final BigDecimal delta2 = BigDecimal.valueOf(-54);
        System.out.println("D1=" + delta1 + ", D2=" + delta2);
        //D1=-54, D2=52
        final BigDecimal bP = BigDecimal.valueOf(2000 * 100);
        final BigDecimal oPL = BigDecimal.valueOf(1900);
        final BigDecimal oPS = BigDecimal.valueOf(0);
        final BordersService.TradingSignal signal = bordersService.checkBordersForTests(bOb, oOb, delta1, delta2, bP, oPL, oPS);

        System.out.println(signal.toString());

    }

    /**
     * bug https://trello.com/c/quiQP31L/622-26ma19-%D0%BD%D0%B5%D0%BF%D1%80%D0%B0%D0%B2%D0%B8%D0%BB%D1%8C%D0%BD%D1%8B%D0%B9-fixed-block fixed block обрезается
     * до ближайшей ступени. Этого не должно быть.
     *
     * Fixed block включает все попадающие уровни.
     */
    @Test
    public void test_Fixed_okMode_several_steps() {
//        B_delta: +52
//        O_delta: -54
        borderParams.setPosMode(BorderParams.PosMode.RIGHT_MODE);
        settings.getPlacingBlocks().setActiveVersion(PlacingBlocks.Ver.FIXED);
        settings.getPlacingBlocks().setFixedBlockUsd(BigDecimal.valueOf(700 * 100)); // max 2000, but we can do only 600.

        // delta1 == // b_bid[0] - o_ask[1]
        final BigDecimal delta1 = BigDecimal.valueOf(50);
        final BigDecimal delta2 = BigDecimal.valueOf(-10);
        System.out.println("D1=" + delta1 + ", D2=" + delta2);
        final BigDecimal bP = BigDecimal.valueOf(2000 * 100);
        final BigDecimal oPL = BigDecimal.valueOf(1400); // take id=3 to 1500 ; id=4 to 2000 (stop here because there is no id=5)
        final BigDecimal oPS = BigDecimal.valueOf(0);
        final BordersService.TradingSignal signal = bordersService.checkBordersForTests(bOb, oOb, delta1, delta2, bP, oPL, oPS);

        System.out.println(signal.toString());

        Assert.assertEquals(signal.tradeType, TradeType.DELTA1_B_SELL_O_BUY);
        assertEquals(signal.okexBlock, 600);
    }

    @Test
    @Ignore
    public void test_Dynamic_b_br_close() {
//        B_delta: +52
//        O_delta: -54
        borderParams.setPosMode(BorderParams.PosMode.RIGHT_MODE);
        settings.getPlacingBlocks().setActiveVersion(PlacingBlocks.Ver.DYNAMIC);
        settings.getPlacingBlocks().setDynMaxBlockUsd(BigDecimal.valueOf(20 * 100));

        // delta1 == // b_bid[0] - o_ask[1]
        final BigDecimal delta1 = BigDecimal.valueOf(52);
        final BigDecimal delta2 = BigDecimal.valueOf(-54);
        //D1=-54, D2=52
        final BigDecimal bP = BigDecimal.valueOf(2000 * 100);
        final BigDecimal oPL = BigDecimal.valueOf(400); // +400 -2300  (-1900)
        final BigDecimal oPS = BigDecimal.valueOf(2300);
        final BordersService.TradingSignal signal = bordersService.checkBordersForTests(bOb, oOb, delta1, delta2, bP, oPL, oPS);

        System.out.println(signal.toString());
        assertEquals("b_br_close", signal.borderName);
        assertEquals(17, signal.okexBlock);
    }

    private BorderParams createDefaultBorders2() {
        final List<BorderTable> borders = new ArrayList<>();
        final List<BorderItem> borderBtmClose = new ArrayList<>();
        borderBtmClose.add(new BorderItem(1, BigDecimal.valueOf(90), 0, 0));
        borderBtmClose.add(new BorderItem(2, BigDecimal.valueOf(80), 500, 500));
        borderBtmClose.add(new BorderItem(3, BigDecimal.valueOf(70), 1000, 1000));
        borderBtmClose.add(new BorderItem(4, BigDecimal.valueOf(60), 1500, 1500));
        borders.add(new BorderTable("b_br_close", borderBtmClose));
        final List<BorderItem> borderBtmOpen = new ArrayList<>();
        borderBtmOpen.add(new BorderItem(1, BigDecimal.valueOf(20), 500, 500));
        borderBtmOpen.add(new BorderItem(2, BigDecimal.valueOf(30), 1000, 1000));
        borderBtmOpen.add(new BorderItem(3, BigDecimal.valueOf(40), 1500, 1500));
        borderBtmOpen.add(new BorderItem(4, BigDecimal.valueOf(50), 2000, 2000));
        borders.add(new BorderTable("b_br_open", borderBtmOpen));
        final List<BorderItem> borderOkexClose = new ArrayList<>();
        borderOkexClose.add(new BorderItem(1, BigDecimal.valueOf(160), 0, 0));
        borderOkexClose.add(new BorderItem(2, BigDecimal.valueOf(150), 500, 500));
        borderOkexClose.add(new BorderItem(3, BigDecimal.valueOf(140), 1000, 1000));
        borderOkexClose.add(new BorderItem(4, BigDecimal.valueOf(130), 1500, 1500));
        borders.add(new BorderTable("o_br_close", borderOkexClose));
        final List<BorderItem> borderOkexOpen = new ArrayList<>();
        borderOkexOpen.add(new BorderItem(1, BigDecimal.valueOf(170), 500, 500));
        borderOkexOpen.add(new BorderItem(2, BigDecimal.valueOf(180), 1000, 1000));
        borderOkexOpen.add(new BorderItem(3, BigDecimal.valueOf(190), 1500, 1500));
        borderOkexOpen.add(new BorderItem(4, BigDecimal.valueOf(200), 2000, 2000));
        borders.add(new BorderTable("o_br_open", borderOkexOpen));

        return TestingMocks.toUsd(new BorderParams(BorderParams.Ver.V2, new BordersV1(), new BordersV2(borders)));
    }

    @Test
    public void test_Dynamic_okMode_b_br_open() {
        borderParams = createDefaultBorders2();
        when(persistenceService.fetchBorders()).thenReturn(borderParams);
        when(persistenceService.getSettingsRepositoryService()).thenReturn(settingsRepositoryService);

//        B_delta: +52
//        O_delta: -54
        borderParams.setPosMode(BorderParams.PosMode.RIGHT_MODE);
        settings.getPlacingBlocks().setActiveVersion(PlacingBlocks.Ver.DYNAMIC);
        settings.getPlacingBlocks().setDynMaxBlockUsd(BigDecimal.valueOf(20 * 100));

        // delta1 == // b_bid[0] - o_ask[1]
        final BigDecimal delta1 = BigDecimal.valueOf(52);
        final BigDecimal delta2 = BigDecimal.valueOf(-54);
        System.out.println("D1=" + delta1 + ", D2=" + delta2);
        //D1=-54, D2=52
        final BigDecimal bP = BigDecimal.valueOf(2000 * 100);
        final BigDecimal oPL = BigDecimal.valueOf(400); // +400 -2300  (-1900)
        final BigDecimal oPS = BigDecimal.valueOf(2300);
        final BordersService.TradingSignal signal = bordersService.checkBordersForTests(bOb, oOb, delta1, delta2, bP, oPL, oPS);

//        assertEquals("b_br_open", signal.borderName);
//        assertEquals(2, signal.okexBlock);
        assertEquals(BordersService.TradeType.NONE, signal.tradeType); // o_br_close only when okex_pos>0
        System.out.println(signal.toString());
    }

    private BorderParams createDefaultBorders3() {
        final List<BorderTable> borders = new ArrayList<>();
        final List<BorderItem> borderBtmClose = new ArrayList<>();
        borderBtmClose.add(new BorderItem(1, BigDecimal.valueOf(90), 0, 0));
        borderBtmClose.add(new BorderItem(2, BigDecimal.valueOf(80), 500, 500));
        borderBtmClose.add(new BorderItem(3, BigDecimal.valueOf(70), 1000, 1000));
        borderBtmClose.add(new BorderItem(4, BigDecimal.valueOf(60), 1500, 1500));
        borders.add(new BorderTable("b_br_close", borderBtmClose));
        final List<BorderItem> borderBtmOpen = new ArrayList<>();
        borderBtmOpen.add(new BorderItem(1, BigDecimal.valueOf(20), 500, 500));
        borderBtmOpen.add(new BorderItem(2, BigDecimal.valueOf(30), 1000, 1000));
        borderBtmOpen.add(new BorderItem(3, BigDecimal.valueOf(40), 1500, 1500));
        borderBtmOpen.add(new BorderItem(4, BigDecimal.valueOf(50), 2000, 2000));
        borders.add(new BorderTable("b_br_open", borderBtmOpen));
        final List<BorderItem> borderOkexClose = new ArrayList<>();
        borderOkexClose.add(new BorderItem(1, BigDecimal.valueOf(160), 0, 0));
        borderOkexClose.add(new BorderItem(2, BigDecimal.valueOf(150), 500, 500));
        borderOkexClose.add(new BorderItem(3, BigDecimal.valueOf(140), 1000, 1000));
        borderOkexClose.add(new BorderItem(4, BigDecimal.valueOf(130), 1500, 1500));
        borders.add(new BorderTable("o_br_close", borderOkexClose));
        final List<BorderItem> borderOkexOpen = new ArrayList<>();
        borderOkexOpen.add(new BorderItem(1, BigDecimal.valueOf(10), 500, 500));
        borderOkexOpen.add(new BorderItem(2, BigDecimal.valueOf(20), 1000, 1000));
        borderOkexOpen.add(new BorderItem(3, BigDecimal.valueOf(30), 1500, 1500));
        borderOkexOpen.add(new BorderItem(4, BigDecimal.valueOf(40), 2000, 2000));
        borders.add(new BorderTable("o_br_open", borderOkexOpen));

        return TestingMocks.toUsd(new BorderParams(BorderParams.Ver.V2, new BordersV1(), new BordersV2(borders)));
    }

    @Test
    public void test_Dynamic_okMode_o_br_open() {
        borderParams = createDefaultBorders3();
        when(persistenceService.fetchBorders()).thenReturn(borderParams);
        when(persistenceService.getSettingsRepositoryService()).thenReturn(settingsRepositoryService);

//        B_delta: +52
//        O_delta: -54
        borderParams.setPosMode(BorderParams.PosMode.RIGHT_MODE);
        settings.getPlacingBlocks().setActiveVersion(PlacingBlocks.Ver.DYNAMIC);
        settings.getPlacingBlocks().setDynMaxBlockUsd(BigDecimal.valueOf(20 * 100));

        // delta1 == // b_bid[0] - o_ask[1]
        final BigDecimal delta1 = BigDecimal.valueOf(-54);
        final BigDecimal delta2 = BigDecimal.valueOf(35);
        System.out.println("D1=" + delta1 + ", D2=" + delta2);
        //
        final BigDecimal bP = BigDecimal.valueOf(-150000);
        final BigDecimal oPL = BigDecimal.valueOf(900);
        final BigDecimal oPS = BigDecimal.valueOf(0);
        final BordersService.TradingSignal signal = bordersService.checkBordersForTests(bOb, oOb, delta1, delta2, bP, oPL, oPS);

//        assertEquals("o_br_open", signal.borderName);
//        assertEquals(40, signal.okexBlock);
        assertEquals(BordersService.TradeType.NONE, signal.tradeType); // o_br_open only when okex_pos<0
        System.out.println(signal.toString());
    }

    private BorderParams createDefaultBorders4() {
        final List<BorderTable> borders = new ArrayList<>();
        final List<BorderItem> borderBtmClose = new ArrayList<>();
        borderBtmClose.add(new BorderItem(1, BigDecimal.valueOf(90), 0, 0));
        borderBtmClose.add(new BorderItem(2, BigDecimal.valueOf(80), 500, 500));
        borderBtmClose.add(new BorderItem(3, BigDecimal.valueOf(70), 1000, 1000));
        borderBtmClose.add(new BorderItem(4, BigDecimal.valueOf(60), 1500, 1500));
        borders.add(new BorderTable("b_br_close", borderBtmClose));
        final List<BorderItem> borderBtmOpen = new ArrayList<>();
        borderBtmOpen.add(new BorderItem(1, BigDecimal.valueOf(20), 500, 500));
        borderBtmOpen.add(new BorderItem(2, BigDecimal.valueOf(30), 1000, 1000));
        borderBtmOpen.add(new BorderItem(3, BigDecimal.valueOf(40), 1500, 1500));
        borderBtmOpen.add(new BorderItem(4, BigDecimal.valueOf(50), 2000, 2000));
        borders.add(new BorderTable("b_br_open", borderBtmOpen));
        final List<BorderItem> borderOkexClose = new ArrayList<>();
        borderOkexClose.add(new BorderItem(1, BigDecimal.valueOf(40), 0, 0));
        borderOkexClose.add(new BorderItem(2, BigDecimal.valueOf(30), 500, 500));
        borderOkexClose.add(new BorderItem(3, BigDecimal.valueOf(20), 1000, 1000));
        borderOkexClose.add(new BorderItem(4, BigDecimal.valueOf(10), 1500, 1500));
        borders.add(new BorderTable("o_br_close", borderOkexClose));
        final List<BorderItem> borderOkexOpen = new ArrayList<>();
        borderOkexOpen.add(new BorderItem(1, BigDecimal.valueOf(10), 500, 500));
        borderOkexOpen.add(new BorderItem(2, BigDecimal.valueOf(20), 1000, 1000));
        borderOkexOpen.add(new BorderItem(3, BigDecimal.valueOf(30), 1500, 1500));
        borderOkexOpen.add(new BorderItem(4, BigDecimal.valueOf(40), 2000, 2000));
        borders.add(new BorderTable("o_br_open", borderOkexOpen));

        return TestingMocks.toUsd(new BorderParams(BorderParams.Ver.V2, new BordersV1(), new BordersV2(borders)));
    }

    @Test
    public void test_Dynamic_okMode_o_br_close() {
        borderParams = createDefaultBorders4();
        when(persistenceService.fetchBorders()).thenReturn(borderParams);
        when(persistenceService.getSettingsRepositoryService()).thenReturn(settingsRepositoryService);

//        B_delta: +52
//        O_delta: -54
        borderParams.setPosMode(BorderParams.PosMode.RIGHT_MODE);
        settings.getPlacingBlocks().setActiveVersion(PlacingBlocks.Ver.DYNAMIC);
        settings.getPlacingBlocks().setDynMaxBlockUsd(BigDecimal.valueOf(20 * 100));

        // delta1 == // b_bid[0] - o_ask[1]
        final BigDecimal delta1 = BigDecimal.valueOf(-54);
        final BigDecimal delta2 = BigDecimal.valueOf(35);
        System.out.println("D1=" + delta1 + ", D2=" + delta2);
        //
        final BigDecimal bP = BigDecimal.valueOf(-150000);
        final BigDecimal oPL = BigDecimal.valueOf(1300);
        final BigDecimal oPS = BigDecimal.valueOf(0);
        final BordersService.TradingSignal signal = bordersService.checkBordersForTests(bOb, oOb, delta1, delta2, bP, oPL, oPS);

        assertEquals("o_br_close", signal.borderName);
        assertEquals(20, signal.okexBlock);
        System.out.println(signal.toString());

        System.out.println(signal.borderValueList);
        final DiffFactBr deltaFactBr = ArbUtils.getDeltaFactBr(delta2, signal.borderValueList);
        System.out.println(deltaFactBr.getStr());
        System.out.println(deltaFactBr.getVal());
    }

    /**
     Пример 1. Bitmex_br_open
     Pos_mode == ok_mode
     Pos. Bitmex: -150000, Okex: +1100 -0
     B_delta: +52  ==> okcoin_BUY ==> pos_long_limit
     O_delta: -54  ==> okcoin_SELL
     Max. dyn_block = 2000 контрактов
     Начинаем пересчет с первой строки:
     1 строка - не заходим (не соблюдается условие pos < pos_long_lim)
     2 строка - не заходим
     3 строка:
     рассчитываем динамический шаг для барьера 40. Предположим дин. шаг = 1200 контрактов. У нас квота только на 400, поэтому берем 400 (1500-1100).
     4 строка:
     рассчитываем динамический шаг для барьера 50. Предположим дин. шаг = 700 контрактов. У нас квота только на 500, поэтому берем 500 (2000-1500).
     5 строка и далее - не заходим, b_delta < b_br_open.value
     Таким образом складываем значения и получаем допустимый динамический шаг 400+500 = 900.
     В итоге pos = Bitmex: -60000; Okex: +2000 -0


     -------------
     если x_delta {входит} в

     */
    @Ignore
    @Test
    public void test_Example1() {
        borderParams.setPosMode(BorderParams.PosMode.RIGHT_MODE);
        settings.getPlacingBlocks().setActiveVersion(PlacingBlocks.Ver.DYNAMIC);
        settings.getPlacingBlocks().setDynMaxBlockUsd(BigDecimal.valueOf(2000 * 100));

        // delta1 == // b_bid[0] - o_ask[1]
        final BigDecimal delta1 = BigDecimal.valueOf(52);
        final BigDecimal delta2 = BigDecimal.valueOf(-54);
        final BigDecimal bP = BigDecimal.valueOf(-150000);
        final BigDecimal oPL = BigDecimal.valueOf(1100);
        final BigDecimal oPS = BigDecimal.ZERO;
        final BordersService.TradingSignal signal = bordersService.checkBordersForTests(bOb, oOb, delta1, delta2, bP, oPL, oPS);

        System.out.println(signal.toString());

        assertEquals(17, signal.okexBlock);
        assertEquals("52", signal.deltaVal);
    }

    private void initEthOrderBooks() {
        final Order.OrderType ask = Order.OrderType.ASK;
        final Order.OrderType bid = Order.OrderType.BID;

        final ArrayList<LimitOrder> bAsks = new ArrayList<>();
        bAsks.add(new LimitOrder(ask, BigDecimal.valueOf(100000), CurrencyPair.ETH_USD, "adf1f", new Date(), BigDecimal.valueOf(330.75)));
        bAsks.add(new LimitOrder(ask, BigDecimal.valueOf(100000), CurrencyPair.ETH_USD, "adf1f", new Date(), BigDecimal.valueOf(330.80)));
        final ArrayList<LimitOrder> bBids = new ArrayList<>();
        bBids.add(new LimitOrder(ask, BigDecimal.valueOf(100000), CurrencyPair.ETH_USD, "ba1", new Date(), BigDecimal.valueOf(330.70))); // 1
        bBids.add(new LimitOrder(ask, BigDecimal.valueOf(100000), CurrencyPair.ETH_USD, "ba1", new Date(), BigDecimal.valueOf(330.65))); // 1
        bOb = new OrderBook(new Date(), bAsks, bBids);

        final ArrayList<LimitOrder> oAsks = new ArrayList<>();
        oAsks.add(new LimitOrder(ask, BigDecimal.valueOf(261), CurrencyPair.ETH_USD, "17", new Date(), BigDecimal.valueOf(332.466)));  // 1
        oAsks.add(new LimitOrder(ask, BigDecimal.valueOf(34), CurrencyPair.ETH_USD, "57", new Date(), BigDecimal.valueOf(332.467)));
        oAsks.add(new LimitOrder(ask, BigDecimal.valueOf(60), CurrencyPair.ETH_USD, "68", new Date(), BigDecimal.valueOf(332.468)));
        oAsks.add(new LimitOrder(ask, BigDecimal.valueOf(50), CurrencyPair.ETH_USD, "47", new Date(), BigDecimal.valueOf(332.469)));
        oAsks.add(new LimitOrder(ask, BigDecimal.valueOf(1), CurrencyPair.ETH_USD, "78", new Date(), BigDecimal.valueOf(332.484)));
        final ArrayList<LimitOrder> oBids = new ArrayList<>();
        oBids.add(new LimitOrder(bid, BigDecimal.valueOf(5), CurrencyPair.ETH_USD, "156", new Date(), BigDecimal.valueOf(332.297)));
        oBids.add(new LimitOrder(bid, BigDecimal.valueOf(45), CurrencyPair.ETH_USD, "145", new Date(), BigDecimal.valueOf(332.296)));
        oBids.add(new LimitOrder(bid, BigDecimal.valueOf(214), CurrencyPair.ETH_USD, "13", new Date(), BigDecimal.valueOf(332.295)));
        oBids.add(new LimitOrder(bid, BigDecimal.valueOf(32), CurrencyPair.ETH_USD, "12", new Date(), BigDecimal.valueOf(332.294)));
        oBids.add(new LimitOrder(bid, BigDecimal.valueOf(10), CurrencyPair.ETH_USD, "11", new Date(), BigDecimal.valueOf(332.293)));
        oOb = new OrderBook(new Date(), oAsks, oBids);

    }

    private BorderParams createDefaultBorders_dynAffordable() {
        final List<BorderTable> borders = new ArrayList<>();
        final List<BorderItem> borderBtmClose = new ArrayList<>();
        borders.add(new BorderTable("b_br_close", borderBtmClose));
        final List<BorderItem> borderBtmOpen = new ArrayList<>();
        borders.add(new BorderTable("b_br_open", borderBtmOpen));
        final List<BorderItem> borderOkexClose = new ArrayList<>();
        borders.add(new BorderTable("o_br_close", borderOkexClose));
        final List<BorderItem> borderOkexOpen = new ArrayList<>();
        borderOkexOpen.add(new BorderItem(1, BigDecimal.valueOf(0.1), 40, 40));
        borderOkexOpen.add(new BorderItem(2, BigDecimal.valueOf(0.2), 50, 50));
        borderOkexOpen.add(new BorderItem(3, BigDecimal.valueOf(0.3), 100, 100));
        borderOkexOpen.add(new BorderItem(4, BigDecimal.valueOf(1), 2000, 2000));
//        borderOkexOpen.add(new BorderItem(5, BigDecimal.valueOf(60), 2500, 2500));
        borders.add(new BorderTable("o_br_open", borderOkexOpen));

        return TestingMocks.toUsd(new BorderParams(BorderParams.Ver.V2, new BordersV1(), new BordersV2(borders)), true);
    }

    @Test
    public void test_maxBorder_FIXED_SMALL() {
        initEthOrderBooks();
        borderParams = createDefaultBorders_dynAffordable();
        when(persistenceService.fetchBorders()).thenReturn(borderParams);
        when(persistenceService.getSettingsRepositoryService()).thenReturn(settingsRepositoryService);
        borderParams.setPosMode(BorderParams.PosMode.RIGHT_MODE);
        settings.getPlacingBlocks().setActiveVersion(Ver.FIXED);
        settings.getPlacingBlocks().setFixedBlockUsd(BigDecimal.valueOf(20)); // btm 5 cont, okex 2 cont
//        settings.getPlacingBlocks().setActiveVersion(Ver.DYNAMIC);
//        settings.getPlacingBlocks().setDynMaxBlockUsd(BigDecimal.valueOf(20)); // btm 5 cont, okex 2 cont
        settings.getPlacingBlocks().setCm(BigDecimal.valueOf(2.42));
        settings.getPlacingBlocks().setEth(true);

        // delta1 == // b_bid[0] - o_ask[1]
        final BigDecimal delta1 = BigDecimal.valueOf(-1.766); // 330.70 - 332.466 = -1.766
        final BigDecimal delta2 = BigDecimal.valueOf(1.547); // o_bid - b_ask = 332.297 - 330.75 = 1.547
        final BigDecimal bP = BigDecimal.valueOf(-65);
        final BigDecimal oPL = BigDecimal.ZERO;
        final BigDecimal oPS = BigDecimal.valueOf(3);
        final Affordable firstAffordable = new Affordable(BigDecimal.valueOf(10000), BigDecimal.valueOf(10000));
        final Affordable secondAffordable = new Affordable(BigDecimal.valueOf(10000), BigDecimal.valueOf(10000));
        final BordersService.TradingSignal signal = bordersService.checkBorders(bOb, oOb, delta1, delta2, Pos.posForTests(bP), oPL, oPS, true,
                firstAffordable, secondAffordable);

        System.out.println(signal.toString());

        assertEquals(BigDecimal.valueOf(0.2), signal.getMaxBorder()); // borderItem val=0.3 is skipped, because FIXED step is small
    }

    @Test
    public void test_maxBorder_Dynamic_SMALL() {
        initEthOrderBooks();
        borderParams = createDefaultBorders_dynAffordable();
        when(persistenceService.fetchBorders()).thenReturn(borderParams);
        when(persistenceService.getSettingsRepositoryService()).thenReturn(settingsRepositoryService);
        borderParams.setPosMode(BorderParams.PosMode.RIGHT_MODE);
//        settings.getPlacingBlocks().setActiveVersion(Ver.FIXED);
//        settings.getPlacingBlocks().setFixedBlockUsd(BigDecimal.valueOf(20)); // btm 5 cont, okex 2 cont
        settings.getPlacingBlocks().setActiveVersion(Ver.DYNAMIC);
        settings.getPlacingBlocks().setDynMaxBlockUsd(BigDecimal.valueOf(20)); // btm 5 cont, okex 2 cont
        settings.getPlacingBlocks().setCm(BigDecimal.valueOf(2.42));
        settings.getPlacingBlocks().setEth(true);

        // delta1 == // b_bid[0] - o_ask[1]
        final BigDecimal delta1 = BigDecimal.valueOf(-1.766); // 330.70 - 332.466 = -1.766
        final BigDecimal delta2 = BigDecimal.valueOf(1.547); // o_bid - b_ask = 332.297 - 330.75 = 1.547
        final BigDecimal bP = BigDecimal.valueOf(-65);
        final BigDecimal oPL = BigDecimal.ZERO;
        final BigDecimal oPS = BigDecimal.valueOf(3);
        final Affordable firstAffordable = new Affordable(BigDecimal.valueOf(10000), BigDecimal.valueOf(10000));
        final Affordable secondAffordable = new Affordable(BigDecimal.valueOf(10000), BigDecimal.valueOf(10000));
        final BordersService.TradingSignal signal = bordersService.checkBorders(bOb, oOb, delta1, delta2, Pos.posForTests(bP), oPL, oPS, true,
                firstAffordable, secondAffordable);

        System.out.println(signal.toString());

        assertEquals(BigDecimal.valueOf(0.2), signal.getMaxBorder()); // borderItem val=0.3 is skipped, because FIXED step is small
    }

    @Test
    public void test_maxBorder_FIXED_big_step() {
        initEthOrderBooks();
        borderParams = createDefaultBorders_dynAffordable();
        when(persistenceService.fetchBorders()).thenReturn(borderParams);
        when(persistenceService.getSettingsRepositoryService()).thenReturn(settingsRepositoryService);
        borderParams.setPosMode(BorderParams.PosMode.RIGHT_MODE);
        settings.getPlacingBlocks().setActiveVersion(Ver.FIXED);
        settings.getPlacingBlocks().setFixedBlockUsd(BigDecimal.valueOf(70)); // okex 7 cont when pos -3, borderItem(id=3, pSL=10)
//        settings.getPlacingBlocks().setActiveVersion(Ver.DYNAMIC);
//        settings.getPlacingBlocks().setDynMaxBlockUsd(BigDecimal.valueOf(20)); // btm 5 cont, okex 2 cont
        settings.getPlacingBlocks().setCm(BigDecimal.valueOf(2.42));
        settings.getPlacingBlocks().setEth(true);

        // delta1 == // b_bid[0] - o_ask[1]
        final BigDecimal delta1 = BigDecimal.valueOf(-1.766); // 330.70 - 332.466 = -1.766
        final BigDecimal delta2 = BigDecimal.valueOf(1.547); // o_bid - b_ask = 332.297 - 330.75 = 1.547
        final BigDecimal bP = BigDecimal.valueOf(-65);
        final BigDecimal oPL = BigDecimal.ZERO;
        final BigDecimal oPS = BigDecimal.valueOf(3);
        final Affordable firstAffordable = new Affordable(BigDecimal.valueOf(10000), BigDecimal.valueOf(10000));
        final Affordable secondAffordable = new Affordable(BigDecimal.valueOf(10000), BigDecimal.valueOf(10000));
        final BordersService.TradingSignal signal = bordersService.checkBorders(bOb, oOb, delta1, delta2, Pos.posForTests(bP), oPL, oPS, true,
                firstAffordable, secondAffordable);

        System.out.println(signal.toString());

        assertEquals(BigDecimal.valueOf(0.3), signal.getMaxBorder());
        // okex 7 cont when pos -3, borderItem(id=3, val=0.3, pSL=10)
    }

    @Test
    public void test_maxBorder_DYNAMIC_big_step() {
        initEthOrderBooks();
        borderParams = createDefaultBorders_dynAffordable();
        when(persistenceService.fetchBorders()).thenReturn(borderParams);
        when(persistenceService.getSettingsRepositoryService()).thenReturn(settingsRepositoryService);
        borderParams.setPosMode(BorderParams.PosMode.RIGHT_MODE);
        settings.getPlacingBlocks().setActiveVersion(Ver.DYNAMIC);
        settings.getPlacingBlocks().setDynMaxBlockUsd(BigDecimal.valueOf(70));
        settings.getPlacingBlocks().setCm(BigDecimal.valueOf(2.42));
        settings.getPlacingBlocks().setEth(true);

        // delta1 == // b_bid[0] - o_ask[1]
        final BigDecimal delta1 = BigDecimal.valueOf(-1.766); // 330.70 - 332.466 = -1.766
        final BigDecimal delta2 = BigDecimal.valueOf(1.547); // o_bid - b_ask = 332.297 - 330.75 = 1.547
        final BigDecimal bP = BigDecimal.valueOf(-65);
        final BigDecimal oPL = BigDecimal.ZERO;
        final BigDecimal oPS = BigDecimal.valueOf(3);
        final Affordable firstAffordable = new Affordable(BigDecimal.valueOf(10000), BigDecimal.valueOf(10000));
        final Affordable secondAffordable = new Affordable(BigDecimal.valueOf(10000), BigDecimal.valueOf(10000));
        final BordersService.TradingSignal signal = bordersService.checkBorders(bOb, oOb, delta1, delta2, Pos.posForTests(bP), oPL, oPS, true,
                firstAffordable, secondAffordable);

        System.out.println(signal.toString());

        assertEquals(BigDecimal.valueOf(0.3), signal.getMaxBorder());
        // okex 7 cont when pos -3, borderItem(id=3, val=0.3, pSL=10)
    }

    @Test
    public void test_affordable_DYNAMIC_big_step() {
        initEthOrderBooks();
        borderParams = createDefaultBorders_dynAffordable();
        when(persistenceService.fetchBorders()).thenReturn(borderParams);
        when(persistenceService.getSettingsRepositoryService()).thenReturn(settingsRepositoryService);
        borderParams.setPosMode(BorderParams.PosMode.RIGHT_MODE);
        settings.getPlacingBlocks().setActiveVersion(Ver.DYNAMIC);
        settings.getPlacingBlocks().setDynMaxBlockUsd(BigDecimal.valueOf(70));
        settings.getPlacingBlocks().setCm(BigDecimal.valueOf(2.42));
        settings.getPlacingBlocks().setEth(true);

        // delta1 == // b_bid[0] - o_ask[1]
        final BigDecimal delta1 = BigDecimal.valueOf(-1.766); // 330.70 - 332.466 = -1.766
        final BigDecimal delta2 = BigDecimal.valueOf(1.547); // o_bid - b_ask = 332.297 - 330.75 = 1.547
        final BigDecimal bP = BigDecimal.valueOf(-65);
        final BigDecimal oPL = BigDecimal.ZERO;
        final BigDecimal oPS = BigDecimal.valueOf(3);
        final Affordable firstAffordable = new Affordable(BigDecimal.valueOf(10000), BigDecimal.valueOf(15));
        final Affordable secondAffordable = new Affordable(BigDecimal.valueOf(10000), BigDecimal.valueOf(10000));
        final BordersService.TradingSignal signal = bordersService.checkBorders(bOb, oOb, delta1, delta2, Pos.posForTests(bP), oPL, oPS, true,
                firstAffordable, secondAffordable);

        System.out.println(signal.toString());

        assertEquals(BigDecimal.valueOf(0.3), signal.getMaxBorder());
        // okex 7 cont when pos -3, borderItem(id=3, val=0.3, pSL=10)
        assertEquals(15, signal.bitmexBlock);
        assertEquals(6, signal.okexBlock);
    }

    @Test
    public void check_MaxBorder() {
        borderParams.setPosMode(BorderParams.PosMode.RIGHT_MODE);
//        settings.getPlacingBlocks().setActiveVersion(PlacingBlocks.Ver.DYNAMIC);
//        settings.getPlacingBlocks().setDynMaxBlockUsd(BigDecimal.valueOf(20000 * 100));
        settings.getPlacingBlocks().setActiveVersion(Ver.FIXED);
        settings.getPlacingBlocks().setFixedBlockUsd(BigDecimal.valueOf(30000 * 100));

        // delta1 == // b_bid[0] - o_ask[1]
//        final BigDecimal delta1 = BigDecimal.valueOf(52); // cross 3,4
        final BigDecimal delta1 = BigDecimal.valueOf(62);  // cross 3,4,5
        final BigDecimal delta2 = BigDecimal.valueOf(-54);
        final BigDecimal bP = BigDecimal.valueOf(-140000);
        final BigDecimal oPL = BigDecimal.valueOf(1100);
        final BigDecimal oPS = BigDecimal.ZERO;
        final BordersService.TradingSignal signal = bordersService.checkBordersForTests(bOb, oOb, delta1, delta2, bP, oPL, oPS);

        System.out.println(signal.toString());
        System.out.println("minBorder=" + signal.getMinBorder());
        System.out.println("maxBorder=" + signal.getMaxBorder());
        System.out.println("delta=" + signal.getDelta());
        System.out.println(signal.toBtmFokAutoArgs());


    }

    private BorderParams createDefaultBorders5() {
        final List<BorderTable> borders = new ArrayList<>();
        final List<BorderItem> borderBtmClose = new ArrayList<>();
        borderBtmClose.add(new BorderItem(1, BigDecimal.valueOf(0.4), 0, 0));
        borderBtmClose.add(new BorderItem(2, BigDecimal.valueOf(0.2), 500, 500));
        borderBtmClose.add(new BorderItem(3, BigDecimal.valueOf(0), 1000, 1000));
        borderBtmClose.add(new BorderItem(4, BigDecimal.valueOf(-0.2), 1500, 1500));
        borders.add(new BorderTable("b_br_close", borderBtmClose));
        final List<BorderItem> borderBtmOpen = new ArrayList<>();
        borderBtmOpen.add(new BorderItem(1, BigDecimal.valueOf(0.4), 500, 500));
        borderBtmOpen.add(new BorderItem(2, BigDecimal.valueOf(0.6), 1000, 1000));
        borderBtmOpen.add(new BorderItem(3, BigDecimal.valueOf(0.8), 1500, 1500));
        borderBtmOpen.add(new BorderItem(4, BigDecimal.valueOf(1), 2000, 2000));
        borders.add(new BorderTable("b_br_open", borderBtmOpen));
        final List<BorderItem> borderOkexClose = new ArrayList<>();
        borderOkexClose.add(new BorderItem(1, BigDecimal.valueOf(0), 0, 0));
        borderOkexClose.add(new BorderItem(2, BigDecimal.valueOf(0.2), 500, 500));
        borderOkexClose.add(new BorderItem(3, BigDecimal.valueOf(0.4), 1000, 1000));
        borderOkexClose.add(new BorderItem(4, BigDecimal.valueOf(0.6), 1500, 1500));
        borders.add(new BorderTable("o_br_close", borderOkexClose));
        final List<BorderItem> borderOkexOpen = new ArrayList<>();
        borderOkexOpen.add(new BorderItem(1, BigDecimal.valueOf(0), 500, 500));
        borderOkexOpen.add(new BorderItem(2, BigDecimal.valueOf(0.2), 1000, 1000));
        borderOkexOpen.add(new BorderItem(3, BigDecimal.valueOf(0.4), 1500, 1500));
        borderOkexOpen.add(new BorderItem(4, BigDecimal.valueOf(0.6), 2000, 2000));
        borders.add(new BorderTable("o_br_open", borderOkexOpen));

        return TestingMocks.toUsd(new BorderParams(BorderParams.Ver.V2, new BordersV1(), new BordersV2(borders)));
    }

    @Ignore
    @Test
    public void test_eth_cm() {
        borderParams = createDefaultBorders5();
        when(persistenceService.fetchBorders()).thenReturn(borderParams);
        when(persistenceService.getSettingsRepositoryService()).thenReturn(settingsRepositoryService);

//        B_delta: +52
//        O_delta: -54
        borderParams.setPosMode(BorderParams.PosMode.RIGHT_MODE);
        settings.getPlacingBlocks().setActiveVersion(PlacingBlocks.Ver.DYNAMIC);
        settings.getPlacingBlocks().setDynMaxBlockUsd(BigDecimal.valueOf(40 * 100));
        settings.getPlacingBlocks().setCm(BigDecimal.valueOf(8.21));

        // delta1 == // b_bid[0] - o_ask[1]
        final BigDecimal delta1 = BigDecimal.valueOf(0.7);
        final BigDecimal delta2 = BigDecimal.valueOf(-0.85);
        System.out.println("D1=" + delta1 + ", D2=" + delta2);
        //
        final BigDecimal bP = BigDecimal.valueOf(-1595);
        final BigDecimal oPL = BigDecimal.valueOf(171);
        final BigDecimal oPS = BigDecimal.valueOf(0);
        final BordersService.TradingSignal signal = bordersService.checkBordersForTests(bOb, oOb, delta1, delta2, bP, oPL, oPS);

        System.out.println(signal.toString());
        System.out.println(signal.okexBlock);
        System.out.println(signal.bitmexBlock);
        assertEquals("b_br_open", signal.borderName);
        assertEquals(40, signal.okexBlock);
        assertEquals(328, signal.bitmexBlock); // 40 * cm(8.21)
        assertEquals(39, signal.okexBlock);
        assertEquals(320, signal.bitmexBlock); // 40 * cm(8.21)

        System.out.println(signal.borderValueList);
        final DiffFactBr deltaFactBr = ArbUtils.getDeltaFactBr(delta2, signal.borderValueList);
        System.out.println(deltaFactBr.getStr());
        System.out.println(deltaFactBr.getVal());
    }

}
