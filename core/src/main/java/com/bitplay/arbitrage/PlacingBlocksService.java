package com.bitplay.arbitrage;

import com.bitplay.arbitrage.dto.PlBlocks;
import com.bitplay.persistance.SettingsRepositoryService;
import com.bitplay.persistance.domain.settings.PlacingBlocks;

import org.knowm.xchange.dto.Order;
import org.knowm.xchange.dto.marketdata.OrderBook;
import org.knowm.xchange.dto.trade.LimitOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * Created by Sergey Shurmin on 12/29/17.
 */
@Service
public class PlacingBlocksService {

    private final BigDecimal OKEX_FACTOR = BigDecimal.valueOf(100);

    @Autowired
    SettingsRepositoryService settingsRepositoryService;

    public PlBlocks getPlacingBlocks(OrderBook bitmexOrderBook, OrderBook okexOrderBook,
                                     BigDecimal theBorder, PlacingBlocks.DeltaBase deltaBase, BigDecimal oPL, BigDecimal oPS) {
        final PlBlocks theBlocks;
        final PlacingBlocks placingBlocks = settingsRepositoryService.getSettings().getPlacingBlocks();

        if (placingBlocks.getActiveVersion() == PlacingBlocks.Ver.FIXED) {
            theBlocks = new PlBlocks(placingBlocks.getFixedBlockBitmex(), placingBlocks.getFixedBlockOkex(), PlacingBlocks.Ver.FIXED);
        } else if (placingBlocks.getActiveVersion() == PlacingBlocks.Ver.DYNAMIC) {
            final BigDecimal bMaxBlock = placingBlocks.getDynMaxBlockBitmex();
            if (deltaBase == PlacingBlocks.DeltaBase.B_DELTA) {
                theBlocks = getDynamicBlockByBDelta(bitmexOrderBook, okexOrderBook, theBorder, bMaxBlock, oPL, oPS);
            } else { // O_DELTA
                theBlocks = getDynamicBlockByODelta(bitmexOrderBook, okexOrderBook, theBorder, bMaxBlock, oPL, oPS);
            }
        } else {
            throw new IllegalStateException("Unhandled PlacsingBlocks version");
        }
        return theBlocks;
    }

    public PlBlocks getDynamicBlockByBDelta(OrderBook bitmexOrderBook, OrderBook okexOrderBook,
                                            BigDecimal bBorder, BigDecimal bMaxBlock, BigDecimal oPL, BigDecimal oPS) {
        // b_bid - o_ask
        final List<LimitOrder> bids = bitmexOrderBook.getBids();
        final List<LimitOrder> asks = okexOrderBook.getAsks();

        final BigDecimal[] bBidsAm = bids.stream().map(Order::getTradableAmount).limit(20).toArray(BigDecimal[]::new);
        final BigDecimal[] oAsksAm = asks.stream().map(Order::getTradableAmount).limit(20).map(oAm -> oAm.multiply(OKEX_FACTOR)).toArray(BigDecimal[]::new);

        PlBlocks dynBlock = getDynBlock(bBorder, asks, bids, oAsksAm, bBidsAm, bMaxBlock);

        // okex position bound
        if (oPS.signum() > 0 && dynBlock.getBlockOkex().compareTo(oPS) > 0) { // DELTA1_B_SELL_O_BUY
            dynBlock = new PlBlocks(oPS.multiply(OKEX_FACTOR), oPS, PlacingBlocks.Ver.DYNAMIC);
        }
        return dynBlock;
    }

    public PlBlocks getDynamicBlockByODelta(OrderBook bitmexOrderBook, OrderBook okexOrderBook,
                                            BigDecimal oBorder, BigDecimal bMaxBlock, BigDecimal oPL, BigDecimal oPS) {
        // o_bid - b_ask
        final List<LimitOrder> bids = okexOrderBook.getBids();
        final List<LimitOrder> asks = bitmexOrderBook.getAsks();

        final BigDecimal[] oBidsAm = bids.stream().map(Order::getTradableAmount).limit(20).map(oAm -> oAm.multiply(OKEX_FACTOR)).toArray(BigDecimal[]::new);
        final BigDecimal[] bAsksAm = asks.stream().map(Order::getTradableAmount).limit(20).toArray(BigDecimal[]::new);

        // okex position bound
        PlBlocks dynBlock = getDynBlock(oBorder, asks, bids, bAsksAm, oBidsAm, bMaxBlock);

        if (oPL.signum() > 0 && dynBlock.getBlockOkex().compareTo(oPL) > 0) { // DELTA2_B_BUY_O_SELL
            dynBlock = new PlBlocks(oPL.multiply(OKEX_FACTOR), oPL, PlacingBlocks.Ver.DYNAMIC);
        }
        return dynBlock;
    }

    private PlBlocks getDynBlock(BigDecimal oBorder, List<LimitOrder> asks, List<LimitOrder> bids,
                                 BigDecimal[] asksAm, BigDecimal[] bidsAm, BigDecimal maxBlock) {
        int i = 0;
        int k = 0;
        BigDecimal oBlock = BigDecimal.ZERO;
        BigDecimal delta = bids.get(0).getLimitPrice().subtract(asks.get(0).getLimitPrice());

        BigDecimal amount;
        while (delta.compareTo(oBorder) >= 0 && maxBlock.compareTo(oBlock) > 0
                && k < asksAm.length && i < bidsAm.length) {
            amount = bidsAm[i].subtract(asksAm[k]);
            if (amount.signum() > 0) {
                oBlock = oBlock.add(asksAm[k]);
                bidsAm[i] = amount;
                asksAm[k] = BigDecimal.ZERO;
                k++;
            } else if (amount.signum() < 0) {
                oBlock = oBlock.add(bidsAm[i]);
                bidsAm[i] = BigDecimal.ZERO;
                asksAm[k] = amount.negate();
                i++;
            } else if (amount.signum() == 0) {
                oBlock = oBlock.add(bidsAm[i]);
                bidsAm[i] = BigDecimal.ZERO;
                asksAm[k] = BigDecimal.ZERO;
                i++;
                k++;
            }
            delta = bids.get(i).getLimitPrice().subtract(asks.get(k).getLimitPrice());
        }

        oBlock = oBlock.min(maxBlock);

        oBlock = oBlock.divide(OKEX_FACTOR, 0, RoundingMode.DOWN); // round to OKEX_FACTOR
        return new PlBlocks(oBlock.multiply(OKEX_FACTOR), oBlock, PlacingBlocks.Ver.DYNAMIC);
    }
}
