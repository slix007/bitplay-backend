package com.bitplay.arbitrage;

import com.bitplay.market.MarketService;
import com.bitplay.utils.Utils;

import org.knowm.xchange.dto.Order;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

/**
 * Created by Sergey Shurmin on 7/15/17.
 */
@Service("pos-diff")
public class PosDiffService {

    private static final Logger logger = LoggerFactory.getLogger(com.bitplay.arbitrage.ArbitrageService.class);
    private static final Logger deltasLogger = LoggerFactory.getLogger("DELTAS_LOG");
    private static final Logger signalLogger = LoggerFactory.getLogger("SIGNAL_LOG");
    private static final Logger warningLogger = LoggerFactory.getLogger("WARNING_LOG");
    private final BigDecimal DIFF_FACTOR = BigDecimal.valueOf(100);

    private BigDecimal positionsDiff = BigDecimal.ZERO;
    private BigDecimal positionsDiffWithHedge = BigDecimal.ZERO;

    @Autowired
    private ArbitrageService arbitrageService;

    @Scheduled(fixedDelay = 1000)
    public void calcPositionsEquality() {
        final BigDecimal bP = arbitrageService.getFirstMarketService().getPosition().getPositionLong();
        final BigDecimal oPL = arbitrageService.getSecondMarketService().getPosition().getPositionLong();
        final BigDecimal oPS = arbitrageService.getSecondMarketService().getPosition().getPositionShort();
        final BigDecimal hedgeAmount = arbitrageService.getParams().getHedgeAmount() != null
                ? arbitrageService.getParams().getHedgeAmount()
                : BigDecimal.ZERO;

        final BigDecimal okExPosEquivalent = (oPL.subtract(oPS)).multiply(DIFF_FACTOR);
        positionsDiff = okExPosEquivalent.add(bP);
        positionsDiffWithHedge = positionsDiff.subtract(hedgeAmount);

        writeWarnings(bP, oPL, oPS);

        if (positionsDiffWithHedge.signum() != 0) {
            doCorrection(bP, oPL, oPS, hedgeAmount);
        }
    }

    private void doCorrection(final BigDecimal bP, final BigDecimal oPL, final BigDecimal oPS, final BigDecimal hedgeAmount) {
        // 0. check if ready
        if (arbitrageService.getFirstMarketService().isReadyForArbitrage() && arbitrageService.getSecondMarketService().isReadyForArbitrage()) {
            // 1. What we have to correct
            Order.OrderType orderType;
            BigDecimal correctAmount;
            MarketService marketService;
            final BigDecimal okEquiv = (oPL.subtract(oPS)).multiply(DIFF_FACTOR);
            final BigDecimal bEquiv = bP.subtract(hedgeAmount);
            if (positionsDiffWithHedge.signum() < 0) {
                orderType = Order.OrderType.BID;
                if (bEquiv.compareTo(okEquiv) < 0) {
                    // bitmex buy
                    correctAmount = positionsDiffWithHedge.abs();
                    marketService = arbitrageService.getFirstMarketService();
                } else {
                    // okcoin buy
                    correctAmount = positionsDiffWithHedge.abs().divide(DIFF_FACTOR, 0, BigDecimal.ROUND_HALF_UP);
                    marketService = arbitrageService.getSecondMarketService();
                }
            } else {
                orderType = Order.OrderType.ASK;
                if (bEquiv.compareTo(okEquiv) < 0) {
                    // okcoin sell
                    correctAmount = positionsDiffWithHedge.abs().divide(DIFF_FACTOR, 0, BigDecimal.ROUND_DOWN);
                    marketService = arbitrageService.getSecondMarketService();
                } else {
                    // bitmex sell
                    correctAmount = positionsDiffWithHedge.abs();
                    marketService = arbitrageService.getFirstMarketService();
                }
            }

            // 2. check isAffordable
            if (correctAmount.signum() != 0
                    && marketService.isAffordable(orderType, correctAmount)) {
//                bestQuotes.setArbitrageEvent(BestQuotes.ArbitrageEvent.TRADE_STARTED);
                arbitrageService.setSignalType(SignalType.CORRECTION);
                marketService.setBusy();
                // Market specific params
                marketService.placeOrderOnSignal(orderType, correctAmount, null, SignalType.CORRECTION);
            }
        }
    }

    boolean isPositionsEqual() {

        calcPositionsEquality();

        return positionsDiffWithHedge.signum() == 0;
    }

    public boolean getIsPositionsEqual() {
        return positionsDiffWithHedge.signum() == 0;
    }

    public BigDecimal getPositionsDiff() {
        return positionsDiff;
    }

    private void writeWarnings(final BigDecimal bP, final BigDecimal oPL, final BigDecimal oPS) {
        if (positionsDiffWithHedge.signum() != 0) {
            final String posString = String.format("b_pos=%s, o_pos=%s-%s", Utils.withSign(bP), Utils.withSign(oPL), oPS.toPlainString());
            warningLogger.error("Error: {}", posString);
            deltasLogger.error("Error: {}", posString);
        }

        if (oPL.signum() != 0 && oPS.signum() != 0) {
            final String posString = String.format("b_pos=%s, o_pos=%s-%s", Utils.withSign(bP), Utils.withSign(oPL), oPS.toPlainString());
            warningLogger.error("Warning: {}", posString);
            deltasLogger.error("Warning: {}", posString);
        }
    }
}
