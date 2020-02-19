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
        MarketServicePreliq bitmexService = arbitrageService.getLeftMarketService();
        MarketServicePreliq okCoinService = arbitrageService.getRightMarketService();
        int currPos = 0;
        if (pos_mode == BTM_MODE) {
            Pos position = bitmexService.getPos();
            currPos = position.getPositionLong().intValue();
        } else if (pos_mode == OK_MODE) {
            Pos position = okCoinService.getPos();
            int ok_pos_long = position.getPositionLong().intValue();
            int ok_pos_short = position.getPositionShort().intValue();
            currPos = ok_pos_long - ok_pos_short;
        }
        return currPos;
    }

}
