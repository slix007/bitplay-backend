package com.bitplay.arbitrage;

import com.bitplay.market.model.DqlState;
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
public class DqlStateService {

    private volatile DqlState preliqState = DqlState.ANY_ORDERS;

    private volatile DqlState btmState = DqlState.ANY_ORDERS;
    private volatile DqlState okexState = DqlState.ANY_ORDERS;

    public boolean isPreliq() {
        return preliqState == DqlState.PRELIQ
                || btmState == DqlState.PRELIQ
                || okexState == DqlState.PRELIQ
                ;
    }

    public void tryResetPreliq() {
        if (preliqState == DqlState.PRELIQ) {
            //TODO check if need CLOSE_ONLY
            preliqState = DqlState.ANY_ORDERS;
            log.info("reset DqlState from PRELIQ to ANY_ORDERS");
        }
    }

    public void setPreliqState() {
        preliqState = DqlState.PRELIQ;
    }

    public void updateBtmDqlState(BigDecimal bDQLOpenMin, BigDecimal bDQLCloseMin, BigDecimal dqlCurr) {
        btmState = setMarketState(bDQLOpenMin, bDQLCloseMin, dqlCurr, btmState);
    }

    public void updateOkexDqlState(BigDecimal oDQLOpenMin, BigDecimal oDQLCloseMin, BigDecimal dqlCurr) {
        okexState = setMarketState(oDQLOpenMin, oDQLCloseMin, dqlCurr, okexState);
    }

    private DqlState setMarketState(BigDecimal oDQLOpenMin, BigDecimal oDQLCloseMin, BigDecimal dqlCurr, DqlState currState) {
        final DqlState resState;
        if (dqlCurr == null) {
            resState = DqlState.ANY_ORDERS;
        } else {
            if (dqlCurr.compareTo(oDQLOpenMin) >= 0) {
                resState = DqlState.ANY_ORDERS;
            } else if (dqlCurr.compareTo(oDQLCloseMin) >= 0) {
                resState = DqlState.CLOSE_ONLY;
            } else if (dqlCurr.compareTo(oDQLCloseMin) < 0) {
                resState = DqlState.PRELIQ;
            } else {
                throw new IllegalStateException("Illegal dqlCurr=" + dqlCurr);
            }
        }
        return resState;
    }

    public DqlState getCommonDqlState() {
        if (preliqState == DqlState.PRELIQ || okexState == DqlState.PRELIQ || btmState == DqlState.PRELIQ) {
            return DqlState.PRELIQ;
        }
        if (okexState == DqlState.CLOSE_ONLY || btmState == DqlState.CLOSE_ONLY) {
            return DqlState.CLOSE_ONLY;
        }
        return DqlState.ANY_ORDERS;
    }

//    public void decreaseState(BigDecimal oDQLOpenMin, BigDecimal oDQLCloseMin, BigDecimal dqlCurr) {
//        // keep in mind. IT is for theOtherMarket may be not ok.
//        preliqState.updateAndGet(currState -> {
//            //TODO add state machine ?
//            if (currState == DqlState.ANY_ORDERS) {
//                return stateUpdate;
//            }
//            if (currState == DqlState.CLOSE_ONLY
//                    && stateUpdate != DqlState.ANY_ORDERS) { //skip increase state
//                return stateUpdate;
//            }
//            if (currState == DqlState.PRELIQ) { //skip increase state
//                return DqlState.PRELIQ;
//            }
//            return currState; // do not change if increase state
//        });
//    }
}
