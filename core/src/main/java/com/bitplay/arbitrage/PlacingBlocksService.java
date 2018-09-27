package com.bitplay.arbitrage;

import com.bitplay.arbitrage.dto.PlBlocks;
import com.bitplay.persistance.SettingsRepositoryService;
import com.bitplay.persistance.domain.fluent.DeltaName;
import com.bitplay.persistance.domain.settings.PlacingBlocks;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import org.knowm.xchange.dto.Order;
import org.knowm.xchange.dto.marketdata.OrderBook;
import org.knowm.xchange.dto.trade.LimitOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Created by Sergey Shurmin on 12/29/17.
 */
@Service
public class PlacingBlocksService {

    @Autowired
    SettingsRepositoryService settingsRepositoryService;

    public PlBlocks getPlacingBlocks(OrderBook bitmexOrderBook, OrderBook okexOrderBook,
                                     BigDecimal theBorder, DeltaName deltaName, BigDecimal oPL, BigDecimal oPS) {
        PlBlocks theBlocks;
        final PlacingBlocks placingBlocks = settingsRepositoryService.getSettings().getPlacingBlocks();
        final BigDecimal cm = placingBlocks.getBitmexBlockFactor();

        if (placingBlocks.getActiveVersion() == PlacingBlocks.Ver.FIXED) {
            theBlocks = new PlBlocks(placingBlocks.getFixedBlockBitmex(), placingBlocks.getFixedBlockOkex(), PlacingBlocks.Ver.FIXED);
        } else if (placingBlocks.getActiveVersion() == PlacingBlocks.Ver.DYNAMIC) {
            final BigDecimal bMaxBlock = placingBlocks.getDynMaxBlockBitmex();
            if (deltaName == DeltaName.B_DELTA) {
                theBlocks = getDynamicBlockByBDelta(bitmexOrderBook, okexOrderBook, theBorder, bMaxBlock, cm);
                theBlocks = minByPos(theBlocks, oPS, cm);
            } else { // O_DELTA
                theBlocks = getDynamicBlockByODelta(bitmexOrderBook, okexOrderBook, theBorder, bMaxBlock, cm);
                theBlocks = minByPos(theBlocks, oPL, cm);
            }
        } else {
            throw new IllegalStateException("Unhandled PlacsingBlocks version");
        }
        return theBlocks;
    }

    PlBlocks minByPos(PlBlocks dynBlock, BigDecimal pos, BigDecimal cm) {
        if (pos.signum() > 0 && dynBlock.getBlockOkex().compareTo(pos) > 0) {
            dynBlock = new PlBlocks(pos.multiply(cm).setScale(0, RoundingMode.HALF_UP), pos, PlacingBlocks.Ver.DYNAMIC);
        }
        return dynBlock;
    }

    public PlBlocks getDynamicBlockByBDelta(OrderBook bitmexOrderBook, OrderBook okexOrderBook,
            BigDecimal bBorder, BigDecimal bMaxBlock, BigDecimal cm) {
        // b_bid - o_ask
        final List<LimitOrder> bids = bitmexOrderBook.getBids();
        final List<LimitOrder> asks = okexOrderBook.getAsks();

        final BigDecimal[] bBidsAm = bids.stream().map(Order::getTradableAmount).limit(20).toArray(BigDecimal[]::new);
        final BigDecimal[] oAsksAm = asks.stream().map(Order::getTradableAmount).limit(20).map(oAm -> oAm.multiply(cm)).toArray(BigDecimal[]::new);

        return getDynBlock(bBorder, asks, bids, oAsksAm, bBidsAm, bMaxBlock, cm);
    }

    public PlBlocks getDynamicBlockByODelta(OrderBook bitmexOrderBook, OrderBook okexOrderBook,
            BigDecimal oBorder, BigDecimal bMaxBlock, BigDecimal cm) {
        // o_bid - b_ask
        final List<LimitOrder> bids = okexOrderBook.getBids();
        final List<LimitOrder> asks = bitmexOrderBook.getAsks();

        final BigDecimal[] oBidsAm = bids.stream().map(Order::getTradableAmount).limit(20).map(oAm -> oAm.multiply(cm)).toArray(BigDecimal[]::new);
        final BigDecimal[] bAsksAm = asks.stream().map(Order::getTradableAmount).limit(20).toArray(BigDecimal[]::new);

        return getDynBlock(oBorder, asks, bids, bAsksAm, oBidsAm, bMaxBlock, cm);
    }

    private PlBlocks getDynBlock(BigDecimal xBorder, List<LimitOrder> asks, List<LimitOrder> bids,
            BigDecimal[] asksAm, BigDecimal[] bidsAm, BigDecimal maxBlock, BigDecimal cm) {
        int i = 0;
        int k = 0;
        BigDecimal xBlock = BigDecimal.ZERO;
        BigDecimal ob_delta = bids.get(0).getLimitPrice().subtract(asks.get(0).getLimitPrice());

        BigDecimal amount;
        while (ob_delta.compareTo(xBorder) >= 0
                && isLessThanMaxBlock(maxBlock, xBlock)
                && k < asksAm.length - 1
                && i < bidsAm.length - 1) {
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

        xBlock = maxBlock == null ? xBlock : xBlock.min(maxBlock);

        if (cm.scale() > 0) {
            xBlock = xBlock.divide(cm, 0, RoundingMode.HALF_UP); // fix bug when 1 contract goes to 0
        } else {
            xBlock = xBlock.divide(cm, 0, RoundingMode.DOWN); // round to OKEX_FACTOR
        }

        return new PlBlocks(xBlock.multiply(cm).setScale(0, RoundingMode.HALF_UP), xBlock, PlacingBlocks.Ver.DYNAMIC);
    }

    private boolean isLessThanMaxBlock(BigDecimal maxBlock, BigDecimal xBlock) {
        return maxBlock == null || maxBlock.compareTo(xBlock) > 0;
    }
}
