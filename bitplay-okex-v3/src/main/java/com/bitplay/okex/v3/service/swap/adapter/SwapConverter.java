package com.bitplay.okex.v3.service.swap.adapter;

import com.bitplay.model.SwapSettlement;
import com.bitplay.okex.v3.dto.swap.result.SwapFundingTime;

public class SwapConverter {
    public static SwapSettlement convertFunding(SwapFundingTime t) {
        return new SwapSettlement(t.getFunding_time(), t.getFunding_rate(), t.getEstimated_rate(),
                t.getSettlement_time());
    }
}
