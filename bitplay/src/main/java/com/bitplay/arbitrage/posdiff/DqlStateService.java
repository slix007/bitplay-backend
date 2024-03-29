package com.bitplay.arbitrage.posdiff;

import com.bitplay.external.NotifyType;
import com.bitplay.external.SlackNotifications;
import com.bitplay.market.MarketServicePreliq;
import com.bitplay.market.MarketStaticData;
import com.bitplay.market.model.DqlState;
import com.bitplay.arbitrage.ArbitrageService;
import com.bitplay.arbitrage.dto.ArbType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * Updates from {@link PosDiffService}
 * and
 * {@link MarketServicePreliq}.
 * <p>
 * Uses by {@link ArbitrageService}
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DqlStateService {

    private final SlackNotifications slackNotifications;

    private volatile DqlState leftState = DqlState.ANY_ORDERS;
    private volatile DqlState rightState = DqlState.ANY_ORDERS;


    public boolean isPreliq() {
        return leftState == DqlState.PRELIQ
                || rightState == DqlState.PRELIQ
                || leftState == DqlState.KILLPOS
                || rightState == DqlState.KILLPOS
                ;
    }

    public DqlState updateDqlState(String dSym, ArbType arbType, BigDecimal dqlKillPos, BigDecimal dqlOpenMin, BigDecimal dqlCloseMin, BigDecimal dqlCurr,
                                   BigDecimal dqlLevel) {
        if (arbType == ArbType.LEFT) {
            return updateLeftDqlState(dSym, dqlKillPos, dqlOpenMin, dqlCloseMin, dqlCurr, dqlLevel);
        } else {
            return updateRightDqlState(dSym, dqlKillPos, dqlOpenMin, dqlCloseMin, dqlCurr, dqlLevel);
        }
    }

    public DqlState updateLeftDqlState(String dSym, BigDecimal leftDqlKillPos, BigDecimal bDQLOpenMin, BigDecimal bDQLCloseMin, BigDecimal dqlCurr,
                                       BigDecimal dqlLevel) {
        DqlState currState = this.leftState;
        DqlState resState = defineDqlState(leftDqlKillPos, bDQLOpenMin, bDQLCloseMin, dqlCurr, currState, dqlLevel);
        if (currState != resState) {
            log.info(String.format("left %sState %s => %s", dSym, currState, resState));
        }
        this.leftState = resState;

        if (leftState.isClose()) {
            slackNotifications.sendNotify(NotifyType.BITMEX_DQL_OPEN_MIN, String.format("%s %s(%s) < %s_open_min(%s)",
                    MarketStaticData.BITMEX.getName(), dSym, dqlCurr, dSym, bDQLOpenMin));
        } else {
            slackNotifications.resetThrottled(NotifyType.BITMEX_DQL_OPEN_MIN);
        }
//        log.info("dqlCurr=" + dqlCurr);
        return leftState;
    }

    public DqlState updateRightDqlState(String dSym, BigDecimal rightDqlKillPos, BigDecimal oDQLOpenMin, BigDecimal oDQLCloseMin, BigDecimal dqlCurr, BigDecimal dqlLevel) {
        DqlState currState = this.rightState;
        DqlState resState = defineDqlState(rightDqlKillPos, oDQLOpenMin, oDQLCloseMin, dqlCurr, currState, dqlLevel);
        if (currState != resState) {
            log.info(String.format("right %sState %s => %s", dSym, currState, resState));
        }
        this.rightState = resState;
        if (this.rightState.isClose()) {
            slackNotifications.sendNotify(NotifyType.OKEX_DQL_OPEN_MIN, String.format("%s %s(%s) < %s_open_min(%s)",
                    MarketStaticData.OKEX.getName(), dSym, dqlCurr, dSym, oDQLOpenMin));
        } else {
            slackNotifications.resetThrottled(NotifyType.OKEX_DQL_OPEN_MIN);
        }
        return this.rightState;
    }

    private DqlState defineDqlState(BigDecimal xDQLKillPos, BigDecimal xDQLOpenMin, BigDecimal xDQLCloseMin, BigDecimal dqlCurr, DqlState currState,
                                    BigDecimal dqlLevel) {
        final DqlState resState;
        if (dqlCurr == null) {
            resState = DqlState.ANY_ORDERS;
        } else if (dqlCurr.compareTo(dqlLevel) < 0) {
            resState = currState;
        } else {
            if (dqlCurr.compareTo(xDQLOpenMin) >= 0) {
                resState = DqlState.ANY_ORDERS;
            } else if (dqlCurr.compareTo(xDQLCloseMin) >= 0) {
                resState = DqlState.CLOSE_ONLY;
            } else if (dqlCurr.compareTo(xDQLKillPos) >= 0 && dqlCurr.compareTo(xDQLCloseMin) < 0) {
                resState = DqlState.PRELIQ;
            } else if (dqlCurr.compareTo(xDQLKillPos) < 0) {
                resState = DqlState.KILLPOS;
            } else {
                throw new IllegalStateException("Illegal dqlCurr=" + dqlCurr);
            }
        }
        return resState;
    }

    public DqlState getCommonDqlState() {
        if (rightState == DqlState.KILLPOS || leftState == DqlState.KILLPOS) {
            return DqlState.KILLPOS;
        }
        if (rightState == DqlState.PRELIQ || leftState == DqlState.PRELIQ) {
            return DqlState.PRELIQ;
        }
        if (rightState == DqlState.CLOSE_ONLY || leftState == DqlState.CLOSE_ONLY) {
            return DqlState.CLOSE_ONLY;
        }
        return DqlState.ANY_ORDERS;
    }



}
