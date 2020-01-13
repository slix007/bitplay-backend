package com.bitplay.arbitrage.posdiff;

import com.bitplay.arbitrage.ArbitrageService;
import com.bitplay.market.bitmex.BitmexService;
import com.bitplay.market.model.DqlState;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

/**
 * Updates from {@link com.bitplay.arbitrage.posdiff.PosDiffService}
 * and
 * {@link com.bitplay.market.MarketServicePreliq}.
 * <p>
 * Uses by {@link ArbitrageService}
 */
@Slf4j
@Component
public class DqlStateService {

    private final ScheduledExecutorService preliqScheduler = Executors.newSingleThreadScheduledExecutor(
            new ThreadFactoryBuilder().setNameFormat("bitmex-preliq-thread-%d").build());

    private volatile DqlState preliqState = DqlState.ANY_ORDERS;

    private volatile DqlState btmState = DqlState.ANY_ORDERS;
    private volatile DqlState okexState = DqlState.ANY_ORDERS;

    public boolean isPreliq() {
        return preliqState == DqlState.PRELIQ
                || btmState == DqlState.PRELIQ
                || okexState == DqlState.PRELIQ
                || preliqState == DqlState.KILLPOS
                || btmState == DqlState.KILLPOS
                || okexState == DqlState.KILLPOS
                ;
    }

    public void tryResetPreliq() {
        if (preliqState == DqlState.PRELIQ || preliqState == DqlState.KILLPOS) {
            //TODO check if need CLOSE_ONLY
            preliqState = DqlState.ANY_ORDERS;
            log.info("reset DqlState from PRELIQ to ANY_ORDERS");
        }
    }

    public void setPreliqState(DqlState dqlState) {
        preliqState = dqlState;
    }

//    //TODO remove preliqState. Don't set preliq when killpos.
//    public void setPreliqState(LiqInfo liqInfo, BigDecimal dqlKillPos) {
//        if (liqInfo.getDqlCurr().compareTo(dqlKillPos) < 0) {
//            preliqState = DqlState.KILLPOS;
//        } else {
//            preliqState = DqlState.PRELIQ;
//        }
//    }

    public DqlState updateDqlState(String serviceName, BigDecimal dqlKillPos, BigDecimal dqlOpenMin, BigDecimal dqlCloseMin, BigDecimal dqlCurr) {
        if (serviceName.equals(BitmexService.NAME)) {
            return updateBtmDqlState(dqlKillPos, dqlOpenMin, dqlCloseMin, dqlCurr);
        } else {
            return updateOkexDqlState(dqlKillPos, dqlOpenMin, dqlCloseMin, dqlCurr);
        }
    }

    public DqlState updateBtmDqlState(BigDecimal btmDqlKillPos, BigDecimal bDQLOpenMin, BigDecimal bDQLCloseMin, BigDecimal dqlCurr) {
        btmState = setMarketState(btmDqlKillPos, bDQLOpenMin, bDQLCloseMin, dqlCurr, btmState);
        return btmState;
    }

    public DqlState updateOkexDqlState(BigDecimal okexDqlKillPos, BigDecimal oDQLOpenMin, BigDecimal oDQLCloseMin, BigDecimal dqlCurr) {
        okexState = setMarketState(okexDqlKillPos, oDQLOpenMin, oDQLCloseMin, dqlCurr, okexState);
        return okexState;
    }

    private DqlState setMarketState(BigDecimal xDQLKillPos, BigDecimal xDQLOpenMin, BigDecimal xDQLCloseMin, BigDecimal dqlCurr,
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
        return resState;
    }

    public DqlState getCommonDqlState() {
        if (preliqState == DqlState.KILLPOS || okexState == DqlState.KILLPOS || btmState == DqlState.KILLPOS) {
            return DqlState.KILLPOS;
        }
        if (preliqState == DqlState.PRELIQ || okexState == DqlState.PRELIQ || btmState == DqlState.PRELIQ) {
            return DqlState.PRELIQ;
        }
        if (okexState == DqlState.CLOSE_ONLY || btmState == DqlState.CLOSE_ONLY) {
            return DqlState.CLOSE_ONLY;
        }
        return DqlState.ANY_ORDERS;
    }



}