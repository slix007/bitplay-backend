package com.bitplay.arbitrage;

import com.bitplay.market.MarketServicePreliq;
import com.bitplay.model.Pos;
import com.bitplay.persistance.domain.borders.BorderParams.PosMode;
import lombok.RequiredArgsConstructor;

import static com.bitplay.persistance.domain.borders.BorderParams.PosMode.BTM_MODE;
import static com.bitplay.persistance.domain.borders.BorderParams.PosMode.OK_MODE;

@RequiredArgsConstructor
public class DiffFactBrService {
    private final ArbitrageService arbitrageService;

    int getCurrPos(PosMode pos_mode) {
        MarketServicePreliq left = arbitrageService.getLeftMarketService();
        MarketServicePreliq right = arbitrageService.getRightMarketService();
        int currPos = 0;
        if (pos_mode == BTM_MODE) {
            currPos = left.getPosVal().intValue();
        } else if (pos_mode == OK_MODE) {
            currPos = right.getPosVal().intValue();
        }
        return currPos;
    }

}
