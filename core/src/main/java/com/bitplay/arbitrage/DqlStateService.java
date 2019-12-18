package com.bitplay.arbitrage;

import com.bitplay.market.model.DqlState;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicReference;

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

    private final AtomicReference<DqlState> dqlStateAtomicReference = new AtomicReference<>(DqlState.ANY_ORDERS);

    public void setDqlState(DqlState dqlState) {
        dqlStateAtomicReference.set(dqlState);
    }

    public DqlState getDqlState() {
        return dqlStateAtomicReference.get();
    }

    public boolean isPreliq() {
        return getDqlState() == DqlState.PRELIQ;
    }

    public void tryResetPreliq() {
        final DqlState dqlState = getDqlState();
        if (dqlState == DqlState.PRELIQ) {
            //TODO check if need CLOSE_ONLY
            setDqlState(DqlState.ANY_ORDERS);
            log.info("reset DqlState from PRELIQ to ANY_ORDERS");
        }

    }
}
