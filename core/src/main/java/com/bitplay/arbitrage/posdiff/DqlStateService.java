package com.bitplay.arbitrage.posdiff;

import com.bitplay.arbitrage.ArbitrageService;
import com.bitplay.arbitrage.dto.ArbType;
import com.bitplay.external.NotifyType;
import com.bitplay.external.SlackNotifications;
import com.bitplay.market.MarketStaticData;
import com.bitplay.market.model.DqlState;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * Updates from {@link com.bitplay.arbitrage.posdiff.PosDiffService}
 * and
 * {@link com.bitplay.market.MarketServicePreliq}.
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

    public DqlState updateDqlState(ArbType arbType, BigDecimal dqlKillPos, BigDecimal dqlOpenMin, BigDecimal dqlCloseMin, BigDecimal dqlCurr) {
        if (arbType == ArbType.LEFT) {
            return updateLeftDqlState(dqlKillPos, dqlOpenMin, dqlCloseMin, dqlCurr);
        } else {
            return updateRightDqlState(dqlKillPos, dqlOpenMin, dqlCloseMin, dqlCurr);
        }
    }

    public DqlState updateLeftDqlState(BigDecimal leftDqlKillPos, BigDecimal bDQLOpenMin, BigDecimal bDQLCloseMin, BigDecimal dqlCurr) {
        DqlState currState = this.leftState;
        DqlState resState = defineDqlState(leftDqlKillPos, bDQLOpenMin, bDQLCloseMin, dqlCurr);
        if (currState != resState) {
            log.info(String.format("left DqlState %s => %s", currState, resState));
        }
        this.leftState = resState;

        if (leftState.isClose()) {
            slackNotifications.sendNotify(NotifyType.BITMEX_DQL_OPEN_MIN, String.format("%s DQL(%s) < DQL_open_min(%s)",
                    MarketStaticData.BITMEX.getName(), dqlCurr, bDQLOpenMin));
        } else {
            slackNotifications.resetThrottled(NotifyType.BITMEX_DQL_OPEN_MIN);
        }
//        log.info("dqlCurr=" + dqlCurr);
        return leftState;
    }

    public DqlState updateRightDqlState(BigDecimal rightDqlKillPos, BigDecimal oDQLOpenMin, BigDecimal oDQLCloseMin, BigDecimal dqlCurr) {
        DqlState currState = this.rightState;
        DqlState resState = defineDqlState(rightDqlKillPos, oDQLOpenMin, oDQLCloseMin, dqlCurr);
        if (currState != resState) {
            log.info(String.format("right DqlState %s => %s", currState, resState));
        }
        this.rightState = resState;
        if (this.rightState.isClose()) {
            slackNotifications.sendNotify(NotifyType.OKEX_DQL_OPEN_MIN, String.format("%s DQL(%s) < DQL_open_min(%s)",
                    MarketStaticData.OKEX.getName(), dqlCurr, oDQLOpenMin));
        } else {
            slackNotifications.resetThrottled(NotifyType.OKEX_DQL_OPEN_MIN);
        }
        return this.rightState;
    }

    private DqlState defineDqlState(BigDecimal xDQLKillPos, BigDecimal xDQLOpenMin, BigDecimal xDQLCloseMin, BigDecimal dqlCurr) {
        //TODO send event to check preliq/killpos
        final DqlState resState;
        if (dqlCurr == null) {
            resState = DqlState.ANY_ORDERS;
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
