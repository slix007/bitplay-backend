package com.bitplay.market;

import com.bitplay.persistance.domain.settings.PlacingType;
import com.bitplay.api.dto.ob.LimitsJson;
import com.bitplay.arbitrage.dto.SignalType;
import com.bitplay.xchange.dto.Order;

import java.math.BigDecimal;

public interface LimitsService {

    boolean outsideLimitsForPreliq(BigDecimal currentPos);

    LimitsJson getLimitsJson();

    boolean outsideLimits();

    default boolean outsideLimits(Order.OrderType orderType, PlacingType placingType, SignalType signalType) {
        return outsideLimits();
    }

}
