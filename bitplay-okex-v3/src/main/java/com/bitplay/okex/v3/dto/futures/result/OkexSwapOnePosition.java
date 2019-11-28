package com.bitplay.okex.v3.dto.futures.result;

import com.bitplay.model.Pos;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Data
public class OkexSwapOnePosition {

    String margin_mode;
    Instant timestamp;
    List<OkexSwapPosition> holding;

    public Pos toPos() {
        OkexSwapPosition l = OkexSwapPosition.empty();
        OkexSwapPosition s = OkexSwapPosition.empty();
        for (OkexSwapPosition p : holding) {
            if (p.getSide().equals("long")) {
                l = p;
            } else if (p.getSide().equals("short")) {
                s = p;
            }
        }
        final BigDecimal liquidationPrice = l.getLiquidation_price() != null ? l.getLiquidation_price() : s.getLiquidation_price();
        final BigDecimal leverage = l.getPosition().signum() != 0 ? l.getLeverage() : s.getLeverage();

        BigDecimal longAvailToClose = l.getAvail_position();
        BigDecimal shortAvailToClose = s.getAvail_position();

        return new Pos(
                l.getPosition(),
                s.getPosition(),
                longAvailToClose,
                shortAvailToClose,
                leverage,
                liquidationPrice,
                BigDecimal.ZERO, //mark value
                l.getAvg_cost(),
                s.getAvg_cost(),
                timestamp,
                holding.toString(),
                l.getUnrealized_pnl().add(s.getUnrealized_pnl())
        );
    }


}
