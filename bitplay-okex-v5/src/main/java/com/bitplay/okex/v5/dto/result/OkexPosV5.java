package com.bitplay.okex.v5.dto.result;

import com.bitplay.model.Pos;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Date;
import lombok.Data;

@Data
public class OkexPosV5 {

    private String posId;
    private String posSide;
    private BigDecimal pos;
    private BigDecimal avgPx;
    private BigDecimal lever;
    private BigDecimal liqPx;
    private BigDecimal upl;
    private Date uTime;

    public static Pos toPos(OkexPosV5 p) {
        return new Pos(
                p.getPos(),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                p.getLever(),
                p.getLiqPx() != null ? p.getLiqPx().setScale(2, RoundingMode.HALF_UP) : null,
                BigDecimal.ZERO, //mark value
                p.getAvgPx() == null || p.getAvgPx().signum() == 0 ? BigDecimal.ZERO : p.getAvgPx().setScale(8, RoundingMode.HALF_UP),
                BigDecimal.ZERO,
                p.getUTime() == null ? null : p.getUTime().toInstant(),
                p.toString(),
                p.getUpl(),
                null);
    }

}
