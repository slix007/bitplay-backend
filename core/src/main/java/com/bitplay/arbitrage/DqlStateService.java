package com.bitplay.arbitrage;

import com.bitplay.market.model.DqlState;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicReference;

/**
 * Updates from {@link com.bitplay.arbitrage.posdiff.PosDiffService}
 * and
 * {@link com.bitplay.market.MarketServicePreliq}.
 * <p>
 * Uses by {@link ArbitrageService}
 */
@Component
public class DqlStateService {

    private final AtomicReference<DqlState> dqlStateAtomicReference = new AtomicReference<>(DqlState.ANY_ORDERS);

    public void setDqlState(DqlState dqlState) {
        dqlStateAtomicReference.set(dqlState);
    }

    public DqlState getDqlState() {
        return dqlStateAtomicReference.get();
    }

}
