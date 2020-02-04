package com.bitplay.arbitrage.posdiff;

import com.bitplay.arbitrage.ArbitrageService;
import com.bitplay.external.NotifyType;
import com.bitplay.external.SlackNotifications;
import com.bitplay.market.MarketStaticData;
import com.bitplay.market.bitmex.BitmexService;
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

    private volatile DqlState btmState = DqlState.ANY_ORDERS;
    private volatile DqlState okexState = DqlState.ANY_ORDERS;


    public boolean isPreliq() {
        return btmState == DqlState.PRELIQ
                || okexState == DqlState.PRELIQ
                || btmState == DqlState.KILLPOS
                || okexState == DqlState.KILLPOS
                ;
    }

    public DqlState updateDqlState(String serviceName, BigDecimal dqlKillPos, BigDecimal dqlOpenMin, BigDecimal dqlCloseMin, BigDecimal dqlCurr) {
        if (serviceName.equals(BitmexService.NAME)) {
            return updateBtmDqlState(dqlKillPos, dqlOpenMin, dqlCloseMin, dqlCurr);
        } else {
            return updateOkexDqlState(dqlKillPos, dqlOpenMin, dqlCloseMin, dqlCurr);
        }
    }

    public DqlState updateBtmDqlState(BigDecimal btmDqlKillPos, BigDecimal bDQLOpenMin, BigDecimal bDQLCloseMin, BigDecimal dqlCurr) {
        btmState = defineDqlState(btmDqlKillPos, bDQLOpenMin, bDQLCloseMin, dqlCurr, btmState);
        if (btmState.isClose()) {
            slackNotifications.sendNotify(NotifyType.BITMEX_DQL_OPEN_MIN, String.format("%s DQL(%s) < DQL_open_min(%s)",
                    MarketStaticData.BITMEX.getName(), dqlCurr, bDQLOpenMin));
        } else {
            slackNotifications.resetThrottled(NotifyType.BITMEX_DQL_OPEN_MIN);
        }
//        log.info("dqlCurr=" + dqlCurr);
        return btmState;
    }

    public DqlState updateOkexDqlState(BigDecimal okexDqlKillPos, BigDecimal oDQLOpenMin, BigDecimal oDQLCloseMin, BigDecimal dqlCurr) {
        okexState = defineDqlState(okexDqlKillPos, oDQLOpenMin, oDQLCloseMin, dqlCurr, okexState);
        if (okexState.isClose()) {
            slackNotifications.sendNotify(NotifyType.OKEX_DQL_OPEN_MIN, String.format("%s DQL(%s) < DQL_open_min(%s)",
                    MarketStaticData.OKEX.getName(), dqlCurr, oDQLOpenMin));
        } else {
            slackNotifications.resetThrottled(NotifyType.OKEX_DQL_OPEN_MIN);
        }
        return okexState;
    }

    private DqlState defineDqlState(BigDecimal xDQLKillPos, BigDecimal xDQLOpenMin, BigDecimal xDQLCloseMin, BigDecimal dqlCurr,
                                    DqlState currState) {
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
        if (currState != resState) {
            log.info(String.format("DqlState %s => %s", currState, resState));
        }
        return resState;
    }

    public DqlState getCommonDqlState() {
        if (okexState == DqlState.KILLPOS || btmState == DqlState.KILLPOS) {
            return DqlState.KILLPOS;
        }
        if (okexState == DqlState.PRELIQ || btmState == DqlState.PRELIQ) {
            return DqlState.PRELIQ;
        }
        if (okexState == DqlState.CLOSE_ONLY || btmState == DqlState.CLOSE_ONLY) {
            return DqlState.CLOSE_ONLY;
        }
        return DqlState.ANY_ORDERS;
    }



}
