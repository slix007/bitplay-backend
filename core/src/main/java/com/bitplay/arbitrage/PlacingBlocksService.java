package com.bitplay.arbitrage;

import com.bitplay.arbitrage.dto.PlBlocks;
import com.bitplay.persistance.SettingsRepositoryService;
import com.bitplay.persistance.domain.settings.PlacingBlocks;

import org.knowm.xchange.dto.Order;
import org.knowm.xchange.dto.marketdata.OrderBook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Created by Sergey Shurmin on 12/29/17.
 */
@Service
public class PlacingBlocksService {

    private final BigDecimal OKEX_FACTOR = BigDecimal.valueOf(100);

    @Autowired
    SettingsRepositoryService settingsRepositoryService;

    public PlBlocks getPlacingBlocks(OrderBook bitmexOrderBook, OrderBook okexOrderBook) {
        final PlacingBlocks placingBlocks = settingsRepositoryService.getSettings().getPlacingBlocks();
        if (placingBlocks.getActiveVersion() == PlacingBlocks.Ver.FIXED) {
            return new PlBlocks(placingBlocks.getDynMaxBlockBitmex(), placingBlocks.getDynMaxBlockOkex());
        } else if (placingBlocks.getActiveVersion() == PlacingBlocks.Ver.DYNAMIC) {
            return new PlBlocks(placingBlocks.getDynMaxBlockBitmex(), placingBlocks.getDynMaxBlockOkex());
        }
        throw new IllegalStateException("Unhandled PlacsingBlocks version");
    }

    public PlBlocks getDynamicBlockBitmex(OrderBook bitmexOrderBook, OrderBook okexOrderBook,
                                          BigDecimal bDelta, BigDecimal bBorder, PlacingBlocks blocksSettings) {
        int i = 1;
        int k = 1;
        final BigDecimal[] bBidsAm = bitmexOrderBook.getBids().stream().map(Order::getTradableAmount).toArray(BigDecimal[]::new);
        final BigDecimal[] oAsksAm = okexOrderBook.getAsks().stream().map(Order::getTradableAmount).toArray(BigDecimal[]::new);

        final BigDecimal bMaxBlock = blocksSettings.getDynMaxBlockBitmex();
        BigDecimal bBlock = BigDecimal.ZERO;
        BigDecimal amount = BigDecimal.ZERO;
        BigDecimal bD = bDelta;
        while (bD.compareTo(bBorder) >= 0 && bMaxBlock.compareTo(bBlock) > 0) {
            amount = bBidsAm[i].subtract(oAsksAm[k].multiply(OKEX_FACTOR));
            if (amount.signum() > 0) {
                bBlock = bBlock.add(oAsksAm[k].multiply(OKEX_FACTOR));
                bBidsAm[i] = amount;
                oAsksAm[k] = BigDecimal.ZERO;
                k++;
            } else if (amount.signum() < 0) {
                bBlock = bBlock.add(bBidsAm[i]);
                bBidsAm[i] = BigDecimal.ZERO;
                oAsksAm[k] = amount.negate();
                i++;
            } else if (amount.signum() == 0) {
                bBlock = bBlock.add(bBidsAm[i]);
                bBidsAm[i] = BigDecimal.ZERO;
                oAsksAm[k] = BigDecimal.ZERO;
                i++;
                k++;
            }
            bD = bitmexOrderBook.getBids().get(i).getLimitPrice().subtract(okexOrderBook.getAsks().get(k).getLimitPrice());
        }

        bBlock = bBlock.min(bMaxBlock);

        return new PlBlocks(bBlock, bBlock.divide(OKEX_FACTOR, 0, RoundingMode.HALF_UP));
    }

    public PlBlocks getDynamicBlockOkex(OrderBook bitmexOrderBook, OrderBook okexOrderBook,
                                        BigDecimal oDelta, BigDecimal oBorder, PlacingBlocks blocksSettings) {
        while (ok_d >= ok_border && ok_max_block x 100 > ok_block) do
        {
            am = b_ob_bid_am[i] - ok_ob_ask_am[k] x 100;
            if (am > 0)
            {
                ok_block += ok_ob_ask_am[k] x 100;
                b_ob_bid_am[i] = am;
                ok_ob_ask_am[k] = 0;
                k++;
            }
            if ( am < 0)
            {
                ok_block += b_ob_bid_am[i];
                b_ob_bid_am[i] = 0;
                ok_ob_ask_am[k] = -am;
                i++;
            }
            if (am == 0)
            {
                ok_block += b_ob_bid_am[i];
                b_ob_bid_am[i] = 0;
                ok_ob_ask_am[k] = 0;
                i++;
                k++;
            }

            ok_d = ok_ob_bid_qu[i] - b_ob_ask_qu[k];
        }
        ok_block = min(ok_max_block x 100, ok_block);

        int i = 1;
        int k = 1;
        final BigDecimal[] bAsksAm = bitmexOrderBook.getAsks().stream().map(Order::getTradableAmount).toArray(BigDecimal[]::new);
        final BigDecimal[] oBidsAm = okexOrderBook.getBids().stream().map(Order::getTradableAmount).toArray(BigDecimal[]::new);

        final BigDecimal oMaxBlock = blocksSettings.getDynMaxBlockOkex();
        BigDecimal oBlock = BigDecimal.ZERO;
        BigDecimal amount = BigDecimal.ZERO;
        BigDecimal okD = oDelta;
        while (okD.compareTo(oBorder) >= 0 && oMaxBlock.compareTo(oBlock) > 0) {
            amount = oBidsAm[i].multiply(OKEX_FACTOR).subtract(bAsksAm[k]);
            if (amount.signum() > 0) {
                oBlock = oBlock.add(oBidsAm[k].multiply(OKEX_FACTOR));
                bAsksAm[i] = amount;
                oBidsAm[k] = BigDecimal.ZERO;
                k++;
            } else if (amount.signum() < 0) {
                oBlock = oBlock.add(bAsksAm[i]);
                bAsksAm[i] = BigDecimal.ZERO;
                oBidsAm[k] = amount.negate();
                i++;
            } else if (amount.signum() == 0) {
                oBlock = oBlock.add(bAsksAm[i]);
                bAsksAm[i] = BigDecimal.ZERO;
                oBidsAm[k] = BigDecimal.ZERO;
                i++;
                k++;
            }
            okD = bitmexOrderBook.getBids().get(i).getLimitPrice().subtract(okexOrderBook.getAsks().get(k).getLimitPrice());
        }

        oBlock = oBlock.min(oMaxBlock);

        return new PlBlocks(oBlock, oBlock.divide(OKEX_FACTOR, 0, RoundingMode.HALF_UP));
    }


}
