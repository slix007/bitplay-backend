package com.bitplay.okex.v3.dto.futures.result;

import com.bitplay.model.Pos;
import java.math.BigDecimal;
import java.util.List;
import lombok.Data;

@Data
public class OkexAllPositions {

    Boolean result; // true
    List<List<OkexPosition>> holding;

    public static Pos toPos(OkexPosition p) {
        return new Pos(
                p.getLongQty(),
                p.getShortQty(),
                p.getLongAvailQty(),
                p.getShortAvailQty(),
                p.getLeverage(),
                p.getLiquidationPrice(),
                BigDecimal.ZERO, //mark value
                p.getLongQty().signum() == 0 ? BigDecimal.ZERO : p.getLongAvgCost(),
                p.getShortQty().signum() == 0 ? BigDecimal.ZERO : p.getShortAvgCost(),
                p.getUpdatedAt().toInstant(),
                p.toString(),
                p.getLongPnl().add(p.getShortPnl()),
                null);
    }
}
