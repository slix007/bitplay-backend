package com.bitplay.market.bitmex;

import com.bitplay.arbitrage.exceptions.NotYetInitializedException;
import com.bitplay.market.BalanceService;
import com.bitplay.market.model.FullBalance;
import com.bitplay.persistance.domain.settings.ContractType;
import com.bitplay.utils.Utils;
import java.math.BigDecimal;
import java.math.RoundingMode;
import lombok.AllArgsConstructor;
import org.knowm.xchange.dto.account.AccountInfoContracts;
import org.knowm.xchange.dto.account.Position;
import org.knowm.xchange.dto.marketdata.OrderBook;
import org.springframework.stereotype.Component;

/**
 * Created by Sergey Shurmin on 11/12/17.
 */
@Component
public class BitmexBalanceService implements BalanceService {

    private final BigDecimal ETH_MULTIPLIER = BigDecimal.valueOf(0.000001);

    //    private volatile Instant prevTime = Instant.now();
//    private volatile FullBalance fullBalance;
    @AllArgsConstructor
    private static class Upl {

        final BigDecimal uplBest;
        final BigDecimal uplAvg;
        final String tempValues;
    }

    private FullBalance recalcEquity(AccountInfoContracts accountInfoContracts, Position pObj, OrderBook orderBook, ContractType contractType,
            Position positionXBTUSD, OrderBook orderBookXBTUSD) {

        String tempValues = "";

        final BigDecimal eMark = accountInfoContracts.geteMark();
        final BigDecimal available = accountInfoContracts.getAvailable();
        final BigDecimal margin = eMark != null ? eMark.subtract(available) : BigDecimal.ZERO; //equity and available may be updated with separate responses

        //set eBest & eAvg for accountInfoContracts
        BigDecimal eBest = null;
        BigDecimal eAvg = null;
        if (accountInfoContracts.getWallet() != null && pObj != null && pObj.getPositionLong() != null) {
            final BigDecimal wallet = accountInfoContracts.getWallet();

            Upl upl = calcUpl(contractType.isEth(), pObj, orderBook);
            BigDecimal uplBest = upl.uplBest;
            BigDecimal uplAvg = upl.uplAvg;
            tempValues += upl.tempValues;

            BigDecimal uplBestXBTUSD = BigDecimal.ZERO;
            BigDecimal uplAvgXBTUSD = BigDecimal.ZERO;

            if (contractType.isEth()) {
                tempValues += "<br>";
            }

            if (contractType.isEth() && positionXBTUSD != null && positionXBTUSD.getPositionLong() != null) {
                Upl uplXBTUSD = calcUpl(false, positionXBTUSD, orderBookXBTUSD);
                uplBestXBTUSD = uplXBTUSD.uplBest;
                uplAvgXBTUSD = uplXBTUSD.uplAvg;
                tempValues += uplXBTUSD.tempValues;
            }

            eBest = wallet.add(uplBest).add(uplBestXBTUSD);
            eAvg = wallet.add(uplAvg).add(uplAvgXBTUSD);
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

    private Upl calcUpl(boolean eth, Position pObj, OrderBook orderBook) {
        final BigDecimal pos = pObj.getPositionLong();
        String tempValues = "";
        BigDecimal uplBest = BigDecimal.ZERO;
        BigDecimal uplAvg = BigDecimal.ZERO;

        if (pos.signum() > 0) {
            final BigDecimal entryPrice = pObj.getPriceAvgLong();
            if (entryPrice != null && entryPrice.signum() != 0) {
                final BigDecimal bid1 = Utils.getBestBid(orderBook).getLimitPrice();
                int bidAmount = pObj.getPositionLong().intValue();
                final BigDecimal bidAvgPrice = Utils.getAvgPrice(orderBook, bidAmount, 0);

                if (eth) {
                    //upl_best = (Exit_price - Entry_price) * 0.000001 * cnt, где
                    //Exit_price = bid[1] для long-позиции, ask[1] - для short-позиции;
                    //Entry_price = есть на нашем UI: entry_price long/short (значение берется в соответствии с открытой позицией- long или short);
                    //cnt = количество контрактов в открытой позиции.
                    //upl_avg = (Exit_price - Entry_price) 0.000001 cnt, где
                    //Exit_price = bidAvgPrice для long-позиции, askAvgPrice - для short-позиции, есть на нашем UI. Остальное то же самое, что в upl_best.
                    //e_best = wallet + upl_best;
                    //e_avg = wallet + upl_avg.
                    uplBest = (bid1.subtract(entryPrice)).multiply(ETH_MULTIPLIER).multiply(pos);
                    uplAvg = (bidAvgPrice.subtract(entryPrice)).multiply(ETH_MULTIPLIER).multiply(pos);
                    tempValues += String.format("ETHUSD:bid1=%s,bidAvgPrice=%s", bid1, bidAvgPrice);
                } else {
                    // upl_long = pos/entry_price - pos/bid[1]
                    uplBest = pos.divide(entryPrice, 16, RoundingMode.HALF_UP)
                            .subtract(pos.divide(bid1, 16, RoundingMode.HALF_UP))
                            .setScale(8, RoundingMode.HALF_UP);
                    // upl_long_avg = pos/entry_price - pos/bid[]
                    // e_best = ok_bal + upl_long

                    uplAvg = pos.divide(entryPrice, 16, RoundingMode.HALF_UP)
                            .subtract(pos.divide(bidAvgPrice, 16, RoundingMode.HALF_UP))
                            .setScale(8, RoundingMode.HALF_UP);

                    tempValues += String.format("XBTUSD:bid1=%s,bidAvgPrice=%s", bid1, bidAvgPrice);
                }

            }
        } else if (pos.signum() < 0) {
            final BigDecimal entryPrice = pObj.getPriceAvgLong();
            if (entryPrice != null && entryPrice.signum() != 0) {
                final BigDecimal ask1 = Utils.getBestAsk(orderBook).getLimitPrice();
                int askAmount = pObj.getPositionLong().abs().intValue();
                final BigDecimal askAvgPrice = Utils.getAvgPrice(orderBook, 0, askAmount);

                if (eth) {
                    uplBest = (ask1.subtract(entryPrice)).multiply(ETH_MULTIPLIER).multiply(pos);
                    uplAvg = (askAvgPrice.subtract(entryPrice)).multiply(ETH_MULTIPLIER).multiply(pos);
                    tempValues += String.format("ETHUSD:ask1=%s,askAvgPrice=%s", ask1, askAvgPrice);
                } else {
                    // upl_short = pos / ask[1] - pos / entry_price
                    uplBest = pos.abs().divide(ask1, 16, RoundingMode.HALF_UP)
                            .subtract(pos.abs().divide(entryPrice, 16, RoundingMode.HALF_UP))
                            .setScale(8, RoundingMode.HALF_UP);
                    // e_best = ok_bal + upl_long

                    uplAvg = pos.abs().divide(askAvgPrice, 16, RoundingMode.HALF_UP)
                            .subtract(pos.abs().divide(entryPrice, 16, RoundingMode.HALF_UP))
                            .setScale(8, RoundingMode.HALF_UP);
                    tempValues += String.format("XBTUSD:ask1=%s,askAvgPrice=%s", ask1, askAvgPrice);
                }

            }
        }

        return new Upl(uplBest, uplAvg, tempValues);
    }

    public FullBalance recalcAndGetAccountInfo(AccountInfoContracts accountInfoContracts, Position pObj, OrderBook orderBook, ContractType contractType,
            Position positionXBTUSD, OrderBook orderBookXBTUSD) {
        if (accountInfoContracts == null || pObj == null || orderBook == null) {
            return new FullBalance(null, null, null, null);
        }

//        final Instant nowTime = Instant.now();
//        if (Math.abs(nowTime.toEpochMilli() - prevTime.toEpochMilli()) > 500) { //not often than 0.5 sec
//            fullBalance = recalcEquity(accountInfoContracts, pObj, orderBook);
//            prevTime = nowTime;
//        }
        try {
            return recalcEquity(accountInfoContracts, pObj, orderBook, contractType, positionXBTUSD, orderBookXBTUSD);
        } catch (NotYetInitializedException e) {
            return new FullBalance(null, null, null, null);
        }
    }
}
