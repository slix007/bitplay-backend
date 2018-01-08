package com.bitplay.arbitrage;

import com.bitplay.arbitrage.dto.DynBlocks;
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

    public DynBlocks getDynamicBlockBitmex(OrderBook bitmexOrderBook, OrderBook okexOrderBook,
                                           BigDecimal bBorder, BigDecimal bMaxBlock) {
        // b_bid - o_ask
        final List<LimitOrder> bids = bitmexOrderBook.getBids();
        final List<LimitOrder> asks = okexOrderBook.getAsks();

        final BigDecimal[] bBidsAm = bids.stream().map(Order::getTradableAmount).toArray(BigDecimal[]::new);
        final BigDecimal[] oAsksAm = asks.stream().map(Order::getTradableAmount).map(oAm -> oAm.multiply(OKEX_FACTOR)).toArray(BigDecimal[]::new);

        return getDynBlock(bBorder, asks, bids, oAsksAm, bBidsAm, bMaxBlock);
    }

    public DynBlocks getDynamicBlockOkex(OrderBook bitmexOrderBook, OrderBook okexOrderBook,
                                         BigDecimal oBorder, BigDecimal bMaxBlock) {
        // o_bid - b_ask
        final List<LimitOrder> bids = okexOrderBook.getBids();
        final List<LimitOrder> asks = bitmexOrderBook.getAsks();

        final BigDecimal[] oBidsAm = bids.stream().map(Order::getTradableAmount).map(oAm -> oAm.multiply(OKEX_FACTOR)).toArray(BigDecimal[]::new);
        final BigDecimal[] bAsksAm = asks.stream().map(Order::getTradableAmount).toArray(BigDecimal[]::new);

        return getDynBlock(oBorder, asks, bids, bAsksAm, oBidsAm, bMaxBlock);
    }

    private DynBlocks getDynBlock(BigDecimal oBorder, List<LimitOrder> asks, List<LimitOrder> bids,
                                  BigDecimal[] bAsksAm, BigDecimal[] oBidsAm, BigDecimal maxBlock) {
        int i = 0;
        int k = 0;
        BigDecimal oBlock = BigDecimal.ZERO;
        BigDecimal delta = bids.get(0).getLimitPrice().subtract(asks.get(0).getLimitPrice());

        BigDecimal amount;
        while (delta.compareTo(oBorder) >= 0 && maxBlock.compareTo(oBlock) > 0) {
            amount = oBidsAm[i].subtract(bAsksAm[k]);
            if (amount.signum() > 0) {
                oBlock = oBlock.add(bAsksAm[k]);
                oBidsAm[i] = amount;
                bAsksAm[k] = BigDecimal.ZERO;
                k++;
            } else if (amount.signum() < 0) {
                oBlock = oBlock.add(oBidsAm[i]);
                oBidsAm[i] = BigDecimal.ZERO;
                bAsksAm[k] = amount.negate();
                i++;
            } else if (amount.signum() == 0) {
                oBlock = oBlock.add(oBidsAm[i]);
                oBidsAm[i] = BigDecimal.ZERO;
                bAsksAm[k] = BigDecimal.ZERO;
                i++;
                k++;
            }
            delta = bids.get(i).getLimitPrice().subtract(asks.get(k).getLimitPrice());
        }

        oBlock = oBlock.min(maxBlock);

        oBlock = oBlock.divide(OKEX_FACTOR, 0, RoundingMode.DOWN); // round to OKEX_FACTOR
        return new DynBlocks(oBlock.multiply(OKEX_FACTOR), oBlock);
    }
}
