package com.bitplay.arbitrage;

import static com.bitplay.persistance.domain.borders.BorderParams.PosMode.BTM_MODE;
import static com.bitplay.persistance.domain.borders.BorderParams.PosMode.OK_MODE;

import com.bitplay.market.bitmex.BitmexService;
import com.bitplay.market.okcoin.OkCoinService;
import com.bitplay.persistance.domain.borders.BorderParams.PosMode;
import org.knowm.xchange.dto.account.Position;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class DiffFactBrService {

    @Autowired
    private BitmexService bitmexService;
    @Autowired
    private OkCoinService okCoinService;


    int getCurrPos(PosMode pos_mode) {
        int currPos = 0;
        if (pos_mode == BTM_MODE) {
            Position position = bitmexService.getPosition();
            currPos = position.getPositionLong().intValue();
        } else if (pos_mode == OK_MODE) {
            Position position = okCoinService.getPosition();
            int ok_pos_long = position.getPositionLong().intValue();
            int ok_pos_short = position.getPositionShort().intValue();
            currPos = ok_pos_long - ok_pos_short;
        }
        return currPos;
    }
}
