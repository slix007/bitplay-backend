package com.bitplay.market.okcoin;

import com.bitplay.market.BalanceService;
import com.bitplay.market.model.FullBalance;
import com.bitplay.model.AccountBalance;
import com.bitplay.model.Pos;
import com.bitplay.persistance.SettingsRepositoryService;
import com.bitplay.persistance.domain.settings.ContractType;
import com.bitplay.utils.Utils;
import java.math.BigDecimal;
import java.math.RoundingMode;
import org.knowm.xchange.dto.marketdata.OrderBook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Created by Sergey Shurmin on 11/13/17.
 */
@Component
public class OkcoinBalanceService implements BalanceService {

    private static final Logger logger = LoggerFactory.getLogger(OkcoinBalanceService.class);

    @Autowired
    private SettingsRepositoryService settingsRepositoryService;

//    private volatile Instant prevTime = Instant.now();
//    private volatile FullBalance fullBalance;

    private FullBalance recalcEquity(AccountBalance account, Pos pObj, OrderBook orderBook, ContractType contractType) {

        String tempValues = "";

        final BigDecimal eLast = account.getELast();
        final BigDecimal available = account.getAvailable();
        final BigDecimal margin = eLast != null ? eLast.subtract(available) : BigDecimal.ZERO; //equity and available may be updated with separate responses
        BigDecimal wallet = account.getWallet();

        //set eBest & eAvg for account
        BigDecimal eBest = null;
        BigDecimal eAvg = null;
        if (account.getEMark() != null && pObj != null && pObj.getPositionLong() != null) {
            final BigDecimal pos_cm = contractType.isEth() ? BigDecimal.valueOf(10) : BigDecimal.valueOf(100);
            final BigDecimal pos = (pObj.getPositionLong().subtract(pObj.getPositionShort())).multiply(pos_cm);
            final BigDecimal eMark = account.getEMark();
            final BigDecimal posPnl = pObj.getPosPnl();
            wallet = eMark.subtract(posPnl);

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
                final BigDecimal entryPrice = pObj.getPriceAvgShort();
                if (entryPrice != null && entryPrice.signum() != 0) {
                    final BigDecimal ask1 = Utils.getBestAsk(orderBook).getLimitPrice();
                    // upl_short = pos / ask[1] - pos / entry_price
                    final BigDecimal uplShort = pos.abs().divide(ask1, 16, RoundingMode.HALF_UP)
                            .subtract(pos.abs().divide(entryPrice, 16, RoundingMode.HALF_UP))
                            .setScale(8, RoundingMode.HALF_UP);
                    // e_best = ok_bal + upl_long
                    eBest = wallet.add(uplShort);

                    int askAmount = pObj.getPositionShort().abs().intValue();
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

        if (contractType.isEth()) {
            tempValues += "<br>";
        }

        final boolean okexEbestElast = settingsRepositoryService.getSettings().getOkexEbestElast() != null
                ? settingsRepositoryService.getSettings().getOkexEbestElast()
                : false;
        eBest = okexEbestElast ? eLast : eBest;

        return new FullBalance(new AccountBalance(
                wallet,
                available,
                BigDecimal.ZERO,
                eLast,
                eBest,
                eAvg,
                margin,
                account.getUpl(),
                account.getRpl(),
                account.getRiskRate()
        ),
                pObj,
                tempValues);

    }

    @Override
    public FullBalance recalcAndGetAccountInfo(AccountBalance account, Pos pObj, OrderBook orderBook, ContractType contractType,
            Pos positionXBTUSD, OrderBook orderBookXBTUSD) {
        if (account == null || pObj == null || orderBook == null) {
            logger.error(String.format("Can not calc full balance: account=%s, pObj=%s", account, pObj));
            return new FullBalance(null, null, null);
        }
        return recalcEquity(account, pObj, orderBook, contractType);
    }
}
