package com.bitplay.market.okcoin;

import com.bitplay.market.BalanceService;
import com.bitplay.market.model.FullBalance;
import com.bitplay.model.AccountBalance;
import com.bitplay.model.Pos;
import com.bitplay.persistance.SettingsRepositoryService;
import com.bitplay.persistance.domain.settings.ContractType;
import com.bitplay.persistance.domain.settings.OkexContractType;
import com.bitplay.utils.Utils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.knowm.xchange.dto.marketdata.OrderBook;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Created by Sergey Shurmin on 11/13/17.
 */

@Slf4j
@RequiredArgsConstructor
public class OkexBalanceService implements BalanceService {

    private final SettingsRepositoryService settingsRepositoryService;

    private FullBalance recalcEquity(AccountBalance account, Pos pObj, OrderBook orderBook, ContractType contractType) {

        String tempValues = "";

        final BigDecimal eMark = account.getEMark();
        final BigDecimal eLast = account.getELast();
        final BigDecimal available = account.getAvailable();
        final BigDecimal margin = account.getMargin();
        BigDecimal wallet = account.getWallet();

        //set eBest & eAvg for account
        BigDecimal eBest = wallet;
        BigDecimal eAvg = wallet;
        if (eMark != null
                && pObj != null
                && pObj.getPositionLong() != null
                && pObj.getPriceAvgLong() != null
                && pObj.getPriceAvgShort() != null
        ) {
            final BigDecimal pos_cm = contractType.isQuanto() ? BigDecimal.valueOf(10) : BigDecimal.valueOf(100);
            final BigDecimal plPos = pObj.getPlPos();

            // pl_pos_best =	(1/EntryPrice - 1/BestPrice) * 10 * N
            BigDecimal plPosBestLong = BigDecimal.ZERO;
            BigDecimal plPosBestShort = BigDecimal.ZERO;
            BigDecimal plPosAvgLong = BigDecimal.ZERO;
            BigDecimal plPosAvgShort = BigDecimal.ZERO;

            if (plPos != null) {
                wallet = eMark.subtract(plPos);
            }

            int plPosScale = contractType.getScale() + 2;
            BigDecimal uplLong = BigDecimal.ZERO;
            BigDecimal uplShort = BigDecimal.ZERO;
            BigDecimal uplLongAvg = BigDecimal.ZERO;
            BigDecimal uplShortAvg = BigDecimal.ZERO;
            if (pObj.getPositionLong().signum() > 0) {
                final BigDecimal entryPrice = pObj.getPriceAvgLong();
                if (entryPrice != null && entryPrice.signum() != 0) {
                    final BigDecimal bid1 = Utils.getBestBid(orderBook).getLimitPrice();
                    final BigDecimal pos = (pObj.getPositionLong()).multiply(pos_cm);
                    // upl_long = pos/entry_price - pos/bid[1]
                    uplLong = pos.divide(entryPrice, 16, RoundingMode.HALF_UP)
                            .subtract(pos.divide(bid1, 16, RoundingMode.HALF_UP))
                            .setScale(8, RoundingMode.HALF_UP);
                    plPosBestLong = OkCoinService.calcPlPosValue(pObj.getPositionLong(), entryPrice, bid1, plPosScale);
                    // upl_long_avg = pos/entry_price - pos/bid[]
                    // e_best = ok_bal + upl_long

                    int bidAmount = pObj.getPositionLong().intValue();
                    final BigDecimal bidAvgPrice = Utils.getAvgPrice(orderBook, bidAmount, 0);
                    uplLongAvg = pos.divide(entryPrice, 16, RoundingMode.HALF_UP)
                            .subtract(pos.divide(bidAvgPrice, 16, RoundingMode.HALF_UP))
                            .setScale(8, RoundingMode.HALF_UP);
                    plPosAvgLong = OkCoinService.calcPlPosValue(pObj.getPositionLong(), entryPrice, bidAvgPrice, plPosScale);

                    tempValues += String.format("bid1=%s,bidAvgPrice=%s", bid1, bidAvgPrice);
                }
            }
            if (pObj.getPositionShort().signum() > 0) {

                final BigDecimal entryPrice = pObj.getPriceAvgShort();
                if (entryPrice != null && entryPrice.signum() != 0) {
                    final BigDecimal ask1 = Utils.getBestAsk(orderBook).getLimitPrice();
                    final BigDecimal pos = (pObj.getPositionShort()).multiply(pos_cm);
                    // upl_short = pos / ask[1] - pos / entry_price
                    uplShort = pos.abs().divide(ask1, 16, RoundingMode.HALF_UP)
                            .subtract(pos.abs().divide(entryPrice, 16, RoundingMode.HALF_UP))
                            .setScale(8, RoundingMode.HALF_UP);
                    plPosBestShort = OkCoinService.calcPlPosValue(pObj.getPositionShort().negate(), entryPrice, ask1, plPosScale);

                    int askAmount = pObj.getPositionShort().abs().intValue();
                    final BigDecimal askAvgPrice = Utils.getAvgPrice(orderBook, 0, askAmount);
                    uplShortAvg = pos.abs().divide(askAvgPrice, 16, RoundingMode.HALF_UP)
                            .subtract(pos.abs().divide(entryPrice, 16, RoundingMode.HALF_UP))
                            .setScale(8, RoundingMode.HALF_UP);
                    plPosAvgShort = OkCoinService.calcPlPosValue(pObj.getPositionShort().negate(), entryPrice, askAvgPrice, plPosScale);

                    tempValues += String.format("ask1=%s,askAvgPrice=%s", ask1, askAvgPrice);
                }
            }

            final BigDecimal upl = uplLong.add(uplShort);
            final BigDecimal uplAvg = uplLongAvg.add(uplShortAvg);

            if (contractType instanceof OkexContractType
                    && ((OkexContractType) contractType).isOneFromNewPerpetual()) {
                BigDecimal plPosBest = plPosBestLong.add(plPosBestShort);
                eBest = wallet.add(plPosBest);
                BigDecimal plPosAvg = plPosAvgLong.add(plPosAvgShort);
                eAvg = wallet.add(plPosAvg);
                pObj = pObj.updatePlPosBest(plPosBest);
            } else {
                // old
                eBest = wallet.add(upl);
                eAvg = wallet.add(uplAvg);
            }

        }

        final boolean okexEbestElast = settingsRepositoryService.getSettings().getOkexEbestElast() != null
                ? settingsRepositoryService.getSettings().getOkexEbestElast()
                : false;
        eBest = okexEbestElast ? eLast : eBest;

        return new FullBalance(new AccountBalance(
                wallet,
                available,
                eMark,
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
            log.error(String.format("Can not calc full balance: account=%s, pObj=%s", account, pObj));
            return new FullBalance(null, null, null);
        }
        return recalcEquity(account, pObj, orderBook, contractType);
    }
}
