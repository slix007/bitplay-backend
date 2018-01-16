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
        PlBlocks theBlocks;
        final PlacingBlocks placingBlocks = settingsRepositoryService.getSettings().getPlacingBlocks();

        if (placingBlocks.getActiveVersion() == PlacingBlocks.Ver.FIXED) {
            theBlocks = new PlBlocks(placingBlocks.getFixedBlockBitmex(), placingBlocks.getFixedBlockOkex(), PlacingBlocks.Ver.FIXED);
        } else if (placingBlocks.getActiveVersion() == PlacingBlocks.Ver.DYNAMIC) {
            final BigDecimal bMaxBlock = placingBlocks.getDynMaxBlockBitmex();
            if (deltaBase == PlacingBlocks.DeltaBase.B_DELTA) {
                theBlocks = getDynamicBlockByBDelta(bitmexOrderBook, okexOrderBook, theBorder, bMaxBlock);
                theBlocks = minByPos(theBlocks, oPS);
            } else { // O_DELTA
                theBlocks = getDynamicBlockByODelta(bitmexOrderBook, okexOrderBook, theBorder, bMaxBlock);
                theBlocks = minByPos(theBlocks, oPL);
            }
        } else {
            throw new IllegalStateException("Unhandled PlacsingBlocks version");
        }
        return theBlocks;
    }

    public PlBlocks minByPos(PlBlocks dynBlock, BigDecimal pos) {
        if (pos.signum() > 0 && dynBlock.getBlockOkex().compareTo(pos) > 0) {
            dynBlock = new PlBlocks(pos.multiply(OKEX_FACTOR), pos, PlacingBlocks.Ver.DYNAMIC);
        }
        return dynBlock;
    }

    public PlBlocks getDynamicBlockByBDelta(OrderBook bitmexOrderBook, OrderBook okexOrderBook,
                                            BigDecimal bBorder, BigDecimal bMaxBlock) {
        // b_bid - o_ask
        final List<LimitOrder> bids = bitmexOrderBook.getBids();
        final List<LimitOrder> asks = okexOrderBook.getAsks();

        final BigDecimal[] bBidsAm = bids.stream().map(Order::getTradableAmount).limit(20).toArray(BigDecimal[]::new);
        final BigDecimal[] oAsksAm = asks.stream().map(Order::getTradableAmount).limit(20).map(oAm -> oAm.multiply(OKEX_FACTOR)).toArray(BigDecimal[]::new);

        return getDynBlock(bBorder, asks, bids, oAsksAm, bBidsAm, bMaxBlock);
    }

    public PlBlocks getDynamicBlockByODelta(OrderBook bitmexOrderBook, OrderBook okexOrderBook,
                                            BigDecimal oBorder, BigDecimal bMaxBlock) {
        // o_bid - b_ask
        final List<LimitOrder> bids = okexOrderBook.getBids();
        final List<LimitOrder> asks = bitmexOrderBook.getAsks();

        final BigDecimal[] oBidsAm = bids.stream().map(Order::getTradableAmount).limit(20).map(oAm -> oAm.multiply(OKEX_FACTOR)).toArray(BigDecimal[]::new);
        final BigDecimal[] bAsksAm = asks.stream().map(Order::getTradableAmount).limit(20).toArray(BigDecimal[]::new);

        return getDynBlock(oBorder, asks, bids, bAsksAm, oBidsAm, bMaxBlock);
    }

    private PlBlocks getDynBlock(BigDecimal xBorder, List<LimitOrder> asks, List<LimitOrder> bids,
                                 BigDecimal[] asksAm, BigDecimal[] bidsAm, BigDecimal maxBlock) {
        int i = 0;
        int k = 0;
        BigDecimal xBlock = BigDecimal.ZERO;
        BigDecimal ob_delta = bids.get(0).getLimitPrice().subtract(asks.get(0).getLimitPrice());

        BigDecimal amount;
        while (ob_delta.compareTo(xBorder) >= 0 && maxBlock.compareTo(xBlock) > 0
                && k < asksAm.length - 1 && i < bidsAm.length - 1) {
            amount = bidsAm[i].subtract(asksAm[k]);
            if (amount.signum() > 0) {
                xBlock = xBlock.add(asksAm[k]);
                bidsAm[i] = amount;
                asksAm[k] = BigDecimal.ZERO;
                k++;
            } else if (amount.signum() < 0) {
                xBlock = xBlock.add(bidsAm[i]);
                bidsAm[i] = BigDecimal.ZERO;
                asksAm[k] = amount.negate();
                i++;
            } else if (amount.signum() == 0) {
                xBlock = xBlock.add(bidsAm[i]);
                bidsAm[i] = BigDecimal.ZERO;
                asksAm[k] = BigDecimal.ZERO;
                i++;
                k++;
            }
            ob_delta = bids.get(i).getLimitPrice().subtract(asks.get(k).getLimitPrice());
        }

        xBlock = xBlock.min(maxBlock);

        xBlock = xBlock.divide(OKEX_FACTOR, 0, RoundingMode.DOWN); // round to OKEX_FACTOR
        return new PlBlocks(xBlock.multiply(OKEX_FACTOR), xBlock, PlacingBlocks.Ver.DYNAMIC);
    }
}
