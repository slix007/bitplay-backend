package com.bitplay.market.bitmex;

import com.bitplay.market.dto.FullBalance;
import com.bitplay.utils.Utils;

import org.knowm.xchange.dto.account.AccountInfoContracts;
import org.knowm.xchange.dto.account.Position;
import org.knowm.xchange.dto.marketdata.OrderBook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Created by Sergey Shurmin on 11/12/17.
 */
@Component
public class BitmexBalanceService {
    private final static Logger logger = LoggerFactory.getLogger(BitmexBalanceService.class);

    private FullBalance fullBalance = new FullBalance();



    synchronized void recalcEquity(AccountInfoContracts accountInfoContracts, Position pObj, OrderBook orderBook) {
//        final AccountInfoContracts accountInfoContracts = fullBalance.getAccountInfoContracts() != null
//                ? fullBalance.getAccountInfoContracts() : new AccountInfoContracts();

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
                }
            } else if (pos.signum() < 0) {
                final BigDecimal entryPrice = pObj.getPriceAvgLong();
                if (entryPrice != null && entryPrice.signum() != 0) {
                    final BigDecimal ask1 = Utils.getBestAsk(orderBook).getLimitPrice();
                    // upl_short = pos / ask[1] - pos / entry_price
                    final BigDecimal uplShort = pos.divide(ask1, 16, RoundingMode.HALF_UP)
                            .subtract(pos.divide(entryPrice, 16, RoundingMode.HALF_UP))
                            .setScale(8, RoundingMode.HALF_UP);
                    // e_best = ok_bal + upl_long
                    eBest = wallet.add(uplShort);

                    int askAmount = pObj.getPositionLong().abs().intValue();
                    final BigDecimal askAvgPrice = Utils.getAvgPrice(orderBook, 0, askAmount);
                    final BigDecimal uplLongAvg = pos.divide(askAvgPrice, 16, RoundingMode.HALF_UP)
                            .subtract(pos.divide(entryPrice, 16, RoundingMode.HALF_UP))
                            .setScale(8, RoundingMode.HALF_UP);
                    eAvg = wallet.add(uplLongAvg);
                }
            } else { //pos==0
                // e_best == btm_bal
                eBest = wallet;
                eAvg = wallet;
            }
        }

        fullBalance.setAccountInfoContracts(new AccountInfoContracts(
                newInfo.getWallet() != null ? newInfo.getWallet() : accountInfoContracts.getWallet(),
                available,
                eMark,
                BigDecimal.ZERO,
                eBest,
                eAvg,
                margin,
                newInfo.getUpl() != null ? newInfo.getUpl() : accountInfoContracts.getUpl(),
                newInfo.getRpl() != null ? newInfo.getRpl() : accountInfoContracts.getRpl(),
                newInfo.getRiskRate() != null ? newInfo.getRiskRate() : accountInfoContracts.getRiskRate()
        ));

        logger.debug("Balance " + accountInfoContracts.toString());
    }

}
