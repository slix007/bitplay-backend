package com.bitplay.market.okcoin;

import com.bitplay.api.dto.ob.InsideLimitsEx;
import com.bitplay.api.dto.ob.LimitsJson;
import com.bitplay.arbitrage.dto.SignalType;
import com.bitplay.arbitrage.exceptions.NotYetInitializedException;
import com.bitplay.external.NotifyType;
import com.bitplay.external.SlackNotifications;
import com.bitplay.market.LimitsService;
import com.bitplay.persistance.domain.settings.PlacingType;
import com.bitplay.persistance.SettingsRepositoryService;
import com.bitplay.persistance.domain.fluent.DeltaName;
import com.bitplay.persistance.domain.settings.Limits;
import com.bitplay.persistance.domain.settings.Settings;
import info.bitrich.xchangestream.okexv3.dto.marketdata.OkcoinPriceRange;
import java.math.BigDecimal;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.knowm.xchange.dto.Order.OrderType;
import org.knowm.xchange.dto.marketdata.OrderBook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Created by Sergey Shurmin on 4/8/18.
 */
@Slf4j
@Service
public class OkexLimitsService implements LimitsService {

    private static final Logger warningLogger = LoggerFactory.getLogger("WARNING_LOG");

    @Autowired
    private SlackNotifications slackNotifications;

    @Autowired
    private OkCoinService okCoinService;

    @Autowired
    private SettingsRepositoryService settingsRepositoryService;

    private BigDecimal minPriceForTest;
    private BigDecimal maxPriceForTest;

    private final AtomicBoolean insLimBtmDelta_Buy = new AtomicBoolean(true);
    private final AtomicBoolean insLimOkDelta_Sell = new AtomicBoolean(true);
    private final AtomicBoolean insLimAdjBuy = new AtomicBoolean(true);
    private final AtomicBoolean insLimAdjSell = new AtomicBoolean(true);
    private final AtomicBoolean insLimCorrBuy = new AtomicBoolean(true);
    private final AtomicBoolean insLimCorrSell = new AtomicBoolean(true);

    @AllArgsConstructor
    static class Params {

        Boolean ignoreLimits;
        BigDecimal okexLimitPriceNumber;
        BigDecimal limitBid;
        BigDecimal limitAsk;
        BigDecimal minPrice;
        BigDecimal maxPrice;
    }

    public Params getParams() {
        final OkcoinPriceRange priceRange = okCoinService.getPriceRange();
        if (priceRange == null) {
            throw new NotYetInitializedException();
        }
        final BigDecimal maxPrice = maxPriceForTest != null ? maxPriceForTest : priceRange.getHighest();
        final BigDecimal minPrice = minPriceForTest != null ? minPriceForTest : priceRange.getLowest();

        final Limits limits = settingsRepositoryService.getSettings().getLimits();
        final BigDecimal okexLimitPriceNumber = BigDecimal.ONE; // always 1st //limits.getOkexLimitPrice(); // Limit price, #n
        final int ind = okexLimitPriceNumber.intValue() - 1;
        final OrderBook ob = okCoinService.getOrderBook();
        final BigDecimal limitBid = ob.getBids().get(ind).getLimitPrice();
        final BigDecimal limitAsk = ob.getAsks().get(ind).getLimitPrice();
        return new Params(limits.getIgnoreLimits(), okexLimitPriceNumber, limitBid, limitAsk, minPrice, maxPrice);
    }

    @Override
    public LimitsJson getLimitsJson() {
        final Params p = getParams();
        // insideLimits: Limit Ask < Max price && Limit bid > Min price
//        final boolean insideLimits = (limitAsk.compareTo(maxPrice) < 0 && limitBid.compareTo(minPrice) > 0);
        final Settings settings = settingsRepositoryService.getSettings();
        final PlacingType placingType = settings.getOkexPlacingType();
        final PlacingType adjPlacingType = settings.getPosAdjustment().getPosAdjustmentPlacingType() == PlacingType.TAKER_FOK ? PlacingType.TAKER
                : settings.getPosAdjustment().getPosAdjustmentPlacingType();

        final InsideLimitsEx insideLimitsEx = new InsideLimitsEx();
        final boolean adjBuy = !outsideLimits(OrderType.BID, adjPlacingType, SignalType.ADJ, p);
        final boolean adjSell = !outsideLimits(OrderType.ASK, adjPlacingType, SignalType.ADJ, p);
        final boolean btmDeltaBuy = !outsideLimits(OrderType.BID, placingType, SignalType.AUTOMATIC, p);
        final boolean okDeltaSell = !outsideLimits(OrderType.ASK, placingType, SignalType.AUTOMATIC, p);
        final boolean corrBuy = !outsideLimits(OrderType.BID, PlacingType.TAKER, SignalType.CORR, p);
        final boolean corrSell = !outsideLimits(OrderType.ASK, PlacingType.TAKER, SignalType.CORR, p);
        insideLimitsEx.setBtmDelta(btmDeltaBuy);
        insideLimitsEx.setOkDelta(okDeltaSell);
        insideLimitsEx.setAdjBuy(adjBuy);
        insideLimitsEx.setAdjSell(adjSell);
        insideLimitsEx.setCorrBuy(corrBuy);
        insideLimitsEx.setCorrSell(corrSell);
        final boolean insideLimits = btmDeltaBuy && okDeltaSell && adjBuy && adjSell && corrBuy && corrSell;
        insideLimitsEx.setMain(insideLimits);
        if (insideLimits) {
            slackNotifications.resetThrottled(NotifyType.OKEX_OUTSIDE_LIMITS);
        }

        return new LimitsJson(p.okexLimitPriceNumber, p.limitAsk, p.limitBid, p.minPrice, p.maxPrice,
                insideLimits, insideLimitsEx, p.ignoreLimits, minPriceForTest, maxPriceForTest);
    }

    public boolean outsideLimits(OrderType orderType, PlacingType placingType, SignalType signalType) {

        return outsideLimits(orderType, placingType, signalType == null ? SignalType.AUTOMATIC : signalType, getParams());
    }

    public boolean outsideLimitsOnSignal(DeltaName deltaName, PlacingType placingType) {
        return outsideLimits(deltaName, placingType, SignalType.AUTOMATIC, getParams());
    }

    private boolean outsideLimits(OrderType orderType, PlacingType placingType, SignalType signalType, Params p) {
        //DELTA1_B_SELL_O_BUY
        //DELTA2_B_BUY_O_SELL
        DeltaName deltaName = (orderType == OrderType.BID || orderType == OrderType.EXIT_ASK)
                ? DeltaName.B_DELTA
                : DeltaName.O_DELTA;
        return outsideLimits(deltaName, placingType, signalType, p);
    }

    private boolean outsideLimits(DeltaName deltaName, PlacingType placingType, SignalType signalType, Params p) {
        final Limits limits = settingsRepositoryService.getSettings().getLimits();
        if (limits.getIgnoreLimits()) {
            return false;
        }

        boolean isOutside = false;
        if (deltaName == DeltaName.B_DELTA) {
            //1) OPOT == TAKER or HYBRID, Max price < ask[1];
            //2) OPOT == MAKER, Max price < bid[1];
            //3) OPOT == HYBRID_TICK, Max price < ask[1] - Tick size Okex;
            if (placingType == PlacingType.TAKER_FOK || placingType == PlacingType.TAKER || placingType == PlacingType.HYBRID) {
                if (p.maxPrice.compareTo(p.limitAsk) < 0) {
                    isOutside = true;
                }
            } else if (placingType == PlacingType.MAKER) {
                if (p.maxPrice.compareTo(p.limitBid) < 0) {
                    isOutside = true;
                }
            } else if (placingType == PlacingType.HYBRID_TICK || placingType == PlacingType.MAKER_TICK) {
                final BigDecimal tickSize = okCoinService.getContractType().getTickSize();
                if (p.maxPrice.compareTo(p.limitAsk.subtract(tickSize)) < 0) {
                    isOutside = true;
                }
            }
        } else if (deltaName == DeltaName.O_DELTA) {
            //1) OPOT == TAKER or HYBRID, Min price > bid[1];
            //2) OPOT == MAKER, Min price > ask[1];
            //3) OPOT == HYBRID_TICK, Min price > bid[1] + Tick size Okex;
            if (placingType == PlacingType.TAKER_FOK || placingType == PlacingType.TAKER || placingType == PlacingType.HYBRID) {
                if (p.minPrice.compareTo(p.limitBid) > 0) {
                    isOutside = true;
                }
            } else if (placingType == PlacingType.MAKER) {
                if (p.minPrice.compareTo(p.limitAsk) > 0) {
                    isOutside = true;
                }
            } else if (placingType == PlacingType.HYBRID_TICK || placingType == PlacingType.MAKER_TICK) {
                final BigDecimal tickSize = okCoinService.getContractType().getTickSize();
                if (p.minPrice.compareTo(p.limitBid.add(tickSize)) > 0) {
                    isOutside = true;
                }
            }
        }

        printLogs(isOutside, p, deltaName, signalType);
        // send notification
        if (isOutside) {
            slackNotifications.sendNotify(NotifyType.OKEX_OUTSIDE_LIMITS, OkCoinService.NAME + " outsideLimits");
        }

        return isOutside;
    }

    private void printLogs(boolean isOutside, Params p, DeltaName deltaName, SignalType signalType) {
        StringBuilder partName = new StringBuilder();
        AtomicBoolean insideLimitsArg = definePart(deltaName, signalType, partName);

        printLogs(isOutside, p, insideLimitsArg, partName.toString());
    }

    private AtomicBoolean definePart(DeltaName deltaName, SignalType signalType, StringBuilder partName) {
        //DELTA1_B_SELL_O_BUY
        //DELTA2_B_BUY_O_SELL
        if (deltaName == DeltaName.B_DELTA) {
            if (signalType == null || signalType == SignalType.AUTOMATIC) {
                partName.append("b_delta");
                return insLimBtmDelta_Buy;
            } else if (signalType.isAdj()) {
                partName.append("adj_buy");
                return insLimAdjBuy;
            } else { // is corr
                partName.append("corr/preliq_buy");
                return insLimCorrBuy;
            }
        } else if (deltaName == DeltaName.O_DELTA) {
            if (signalType == null || signalType == SignalType.AUTOMATIC) {
                partName.append("o_delta");
                return insLimOkDelta_Sell;
            } else if (signalType.isAdj()) {
                partName.append("adj_sell");
                return insLimAdjSell;
            } else { // is corr
                partName.append("corr/preliq_sell");
                return insLimCorrSell;
            }
        }
        return new AtomicBoolean();
    }

    private void printLogs(boolean isOutside, Params p, AtomicBoolean insideLimitsArg, String partName) {
        final boolean insideLimits = !isOutside;

        if (insideLimitsArg.compareAndSet(!insideLimits, insideLimits)) {
            String status = insideLimits ? "Inside limits" : "Outside limits";
            final String limitsStr = String.format("Limit ask / Max price = %s / %s ; Limit bid / Min price = %s / %s",
                    p.limitAsk.toPlainString(),
                    p.maxPrice.toPlainString(),
                    p.limitBid.toPlainString(),
                    p.minPrice.toPlainString());
            final String msg = String.format("%s: change okex limits to %s. %s", partName, status, limitsStr);
            warningLogger.warn(msg);
            log.warn(msg);
        }
    }

    @Override
    public boolean outsideLimitsForPreliq(BigDecimal currentPos) {
        final OrderType orderType = currentPos.signum() > 0 ? OrderType.ASK : OrderType.BID;
        return outsideLimits(orderType, PlacingType.TAKER, SignalType.CORR);
    }

    public void setMinPriceForTest(BigDecimal minPriceForTest) {
        this.minPriceForTest = minPriceForTest.signum() == 0 ? null : minPriceForTest;
    }

    public void setMaxPriceForTest(BigDecimal maxPriceForTest) {
        this.maxPriceForTest = maxPriceForTest.signum() == 0 ? null : maxPriceForTest;
    }
}
