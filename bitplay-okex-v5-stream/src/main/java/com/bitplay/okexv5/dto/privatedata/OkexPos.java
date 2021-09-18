package com.bitplay.okexv5.dto.privatedata;

import java.math.BigDecimal;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class OkexPos {

    private BigDecimal availPos;
    private BigDecimal avgPx;
    private String instId;
    private BigDecimal last; // ??
    private BigDecimal lever;
    private BigDecimal liqPx;
    private BigDecimal mmr;
    private BigDecimal margin;
    private BigDecimal pos;
//    private BigDecimal realized_pnl;
//    private BigDecimal settled_pnl;
//    private BigDecimal settlement_price;
    private String side; //long, short
    private Instant timestamp;
    private BigDecimal upl;

    public static OkexPos empty() {
        final OkexPos p = new OkexPos();
        p.availPos = BigDecimal.ZERO;
        p.avgPx = BigDecimal.ZERO;
        p.instId = "";
        p.last = BigDecimal.ZERO;
        p.lever = BigDecimal.ZERO;
        p.liqPx = BigDecimal.ZERO;
        p.mmr = BigDecimal.ZERO;
        p.margin = BigDecimal.ZERO;
        p.pos = BigDecimal.ZERO;
//        p.realized_pnl = BigDecimal.ZERO;
//        p.settled_pnl = BigDecimal.ZERO;
//        p.settlement_price = BigDecimal.ZERO;
        p.side = "";
        p.timestamp = null;
        p.upl = BigDecimal.ZERO;
        return p;
    }

}
