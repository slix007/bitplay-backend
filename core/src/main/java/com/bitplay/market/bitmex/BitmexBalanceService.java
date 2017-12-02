package com.bitplay.market.bitmex;

import com.bitplay.market.BalanceService;
import com.bitplay.market.dto.FullBalance;
import com.bitplay.utils.Utils;

import org.knowm.xchange.dto.account.AccountInfoContracts;
import org.knowm.xchange.dto.account.Position;
import org.knowm.xchange.dto.marketdata.OrderBook;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Created by Sergey Shurmin on 11/12/17.
 */
@Component
public class BitmexBalanceService implements BalanceService {

//    private volatile Instant prevTime = Instant.now();
//    private volatile FullBalance fullBalance;

    private FullBalance recalcEquity(AccountInfoContracts accountInfoContracts, Position pObj, OrderBook orderBook) {

        String tempValues = "";

        final BigDecimal eMark = accountInfoContracts.geteMark();
        final BigDecimal available = accountInfoContracts.getAvailable();
        final BigDecimal margin = eMark != null ? eMark.subtract(available) : BigDecimal.ZERO; //equity and available may be updated with separate responses

        //set eBest & eAvg for accountInfoContracts
        BigDecimal eBest = null;
        BigDecimal eAvg = null;
        if (accountInfoContracts.getWallet() != null && pObj != null && pObj.getPositionLong() != null) {
            final BigDecimal pos = pObj.getPositionLong();
            final BigDecimal wallet = accountInfoContracts.getWallet();

            if (pos.signum() > 0) {
                //TODO how to find entryPrice.
                final BigDecimal entryPrice = pObj.getPriceAvgLong();
                if (entryPrice != null && entryPrice.signum() != 0) {
                    final BigDecimal bid1 = Utils.getBestBid(orderBook).getLimitPrice();
                    // upl_long = pos/entry_price - pos/bid[1]
                    final BigDecimal uplLong = pos.divide(entryPrice, 16, RoundingMode.HALF_UP)
                            .subtract(pos.divide(bid1, 16, RoundingMode.HALF_UP))
                            .setScale(8, RoundingMode.HALF_UP);
                    // upl_long_avg = pos/entry_price - pos/bid[]
                    // e_best = ok_bal + upl_long
                    eBest = wallet.add(uplLong);

                    int bidAmount = pObj.getPositionLong().intValue();
                    final BigDecimal bidAvgPrice = Utils.getAvgPrice(orderBook, bidAmount, 0);
                    final BigDecimal uplLongAvg = pos.divide(entryPrice, 16, RoundingMode.HALF_UP)
                            .subtract(pos.divide(bidAvgPrice, 16, RoundingMode.HALF_UP))
                            .setScale(8, RoundingMode.HALF_UP);
                    eAvg = wallet.add(uplLongAvg);

                    tempValues += String.format("bid1=%s,bidAvgPrice=%s", bid1, bidAvgPrice);
                }
            } else if (pos.signum() < 0) {
                final BigDecimal entryPrice = pObj.getPriceAvgLong();
                if (entryPrice != null && entryPrice.signum() != 0) {
                    final BigDecimal ask1 = Utils.getBestAsk(orderBook).getLimitPrice();
                    // upl_short = pos / ask[1] - pos / entry_price
                    final BigDecimal uplShort = pos.abs().divide(ask1, 16, RoundingMode.HALF_UP)
                            .subtract(pos.abs().divide(entryPrice, 16, RoundingMode.HALF_UP))
                            .setScale(8, RoundingMode.HALF_UP);
                    // e_best = ok_bal + upl_long
                    eBest = wallet.add(uplShort);

                    int askAmount = pObj.getPositionLong().abs().intValue();
                    final BigDecimal askAvgPrice = Utils.getAvgPrice(orderBook, 0, askAmount);
                    final BigDecimal uplLongAvg = pos.abs().divide(askAvgPrice, 16, RoundingMode.HALF_UP)
                            .subtract(pos.abs().divide(entryPrice, 16, RoundingMode.HALF_UP))
                            .setScale(8, RoundingMode.HALF_UP);
                    eAvg = wallet.add(uplLongAvg);

                    tempValues += String.format("ask1=%s,askAvgPrice=%s", ask1, askAvgPrice);
                }
            } else { //pos==0
                // e_best == btm_bal
                eBest = wallet;
                eAvg = wallet;
            }
        }

        return new FullBalance(new AccountInfoContracts(
                accountInfoContracts.getWallet(),
                available,
                eMark,
                BigDecimal.ZERO,
                eBest,
                eAvg,
                margin,
                accountInfoContracts.getUpl(),
                accountInfoContracts.getRpl(),
                accountInfoContracts.getRiskRate()
        ),
                pObj,
                orderBook,
                tempValues);

    }

    public FullBalance recalcAndGetAccountInfo(AccountInfoContracts accountInfoContracts, Position pObj, OrderBook orderBook) {
        if (accountInfoContracts == null || pObj == null || orderBook == null) {
            return new FullBalance(null, null, null, null);
        }

//        final Instant nowTime = Instant.now();
//        if (Math.abs(nowTime.toEpochMilli() - prevTime.toEpochMilli()) > 500) { //not often than 0.5 sec
//            fullBalance = recalcEquity(accountInfoContracts, pObj, orderBook);
//            prevTime = nowTime;
//        }
        return recalcEquity(accountInfoContracts, pObj, orderBook);
    }
}