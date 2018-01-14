package com.bitplay.arbitrage;

import com.bitplay.persistance.PersistenceService;
import com.bitplay.persistance.SettingsRepositoryService;
import com.bitplay.persistance.domain.BorderItem;
import com.bitplay.persistance.domain.BorderParams;
import com.bitplay.persistance.domain.BorderTable;
import com.bitplay.persistance.domain.BordersV1;
import com.bitplay.persistance.domain.BordersV2;
import com.bitplay.persistance.domain.settings.PlacingBlocks;
import com.bitplay.persistance.domain.settings.Settings;
import com.bitplay.utils.Utils;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.dto.Order;
import org.knowm.xchange.dto.marketdata.OrderBook;
import org.knowm.xchange.dto.trade.LimitOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.runners.MockitoJUnitRunner;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/**
 * Created by Sergey Shurmin on 1/9/18.
 */
@RunWith(MockitoJUnitRunner.class)
public class BordersServiceTest {

    @InjectMocks
    BordersService bordersService = new BordersService();

    @Mock
    PersistenceService persistenceService;
    @Mock
    SettingsRepositoryService settingsRepositoryService;

    private OrderBook oOb;
    private OrderBook bOb;

    private void initOrderBooks() {
        final Order.OrderType ask = Order.OrderType.ASK;
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

    private BorderParams createDefaultBorders() {
        final List<BorderTable> borders = new ArrayList<>();
        final List<BorderItem> borderBtmClose = new ArrayList<>();
        borderBtmClose.add(new BorderItem(1, BigDecimal.valueOf(-100), 0, 0));
        borderBtmClose.add(new BorderItem(2, BigDecimal.valueOf(-110), 500, 500));
        borderBtmClose.add(new BorderItem(3, BigDecimal.valueOf(-120), 1000, 1000));
        borderBtmClose.add(new BorderItem(4, BigDecimal.valueOf(-130), 1500, 1500));
        borders.add(new BorderTable("b_br_close", borderBtmClose));
        final List<BorderItem> borderBtmOpen = new ArrayList<>();
        borderBtmOpen.add(new BorderItem(1, BigDecimal.valueOf(-90), 500, 500));
        borderBtmOpen.add(new BorderItem(2, BigDecimal.valueOf(-80), 1000, 1000));
        borderBtmOpen.add(new BorderItem(3, BigDecimal.valueOf(-70), 1500, 1500));
        borderBtmOpen.add(new BorderItem(4, BigDecimal.valueOf(-60), 2000, 2000));
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

        return new BorderParams(BorderParams.Ver.V2, new BordersV1(), new BordersV2(borders));
    }


    @Before
    public void init() {
        MockitoAnnotations.initMocks(this);
        initOrderBooks();

        final Settings settings = new Settings();
        final PlacingBlocks placingBlocks = new PlacingBlocks();
        placingBlocks.setActiveVersion(PlacingBlocks.Ver.FIXED);
        placingBlocks.setFixedBlockOkex(BigDecimal.ONE);
        settings.setPlacingBlocks(placingBlocks);

        when(settingsRepositoryService.getSettings()).thenReturn(settings);

        final BorderParams borderParams = createDefaultBorders();

        when(persistenceService.fetchBorders()).thenReturn(borderParams);

        when(persistenceService.getSettingsRepositoryService()).thenReturn(settingsRepositoryService);
    }

    @Test
    public void testBorders() {
        // delta1 == // b_bid[0] - o_ask[1]
        final BigDecimal delta1 = Utils.getBestBid(bOb).getLimitPrice().subtract(Utils.getBestAsk(oOb).getLimitPrice());
        final BigDecimal delta2 = Utils.getBestBid(oOb).getLimitPrice().subtract(Utils.getBestAsk(bOb).getLimitPrice());
        final BigDecimal bP = BigDecimal.valueOf(2);
        final BigDecimal oPL = BigDecimal.valueOf(2);
        final BigDecimal oPS = BigDecimal.ZERO;
        final BordersService.TradingSignal signal = bordersService.checkBorders(bOb, oOb, delta1, delta2, bP, oPL, oPS);

        System.out.println(signal.toString());

    }

}