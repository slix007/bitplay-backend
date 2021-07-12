package com.bitplay.arbitrage;

import com.bitplay.market.MarketServicePreliq;
import com.bitplay.persistance.domain.borders.BorderParams.PosMode;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class DiffFactBrService {
    private final ArbitrageService arbitrageService;

    int getCurrPos(PosMode pos_mode) {
        MarketServicePreliq left = arbitrageService.getLeftMarketService();
        MarketServicePreliq right = arbitrageService.getRightMarketService();
        int currPos = 0;
        if (pos_mode == PosMode.LEFT_MODE) {
            currPos = left.getPosVal().intValue();
        } else if (pos_mode == PosMode.RIGHT_MODE) {
            currPos = right.getPosVal().intValue();
        }
        return currPos;
    }

}
