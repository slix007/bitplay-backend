package com.bitplay.okex.v5.dto.result;

import java.math.BigDecimal;
import java.time.Instant;
import lombok.Data;

@Data
public class OkexSwapPosition {

    private BigDecimal avail_position;
    private BigDecimal avg_cost;
    private String instrument_id; // "ETH-USD-SWAP"
    private BigDecimal last;
    private BigDecimal leverage;
    private BigDecimal liquidation_price;
    private BigDecimal maint_margin_ratio;
    private BigDecimal margin;
    private BigDecimal position;
    private BigDecimal realized_pnl;
    private BigDecimal settled_pnl;
    private BigDecimal settlement_price;
    private String side; //long, short
    private Instant timestamp;
    private BigDecimal unrealized_pnl;

    public static OkexSwapPosition empty() {
        final OkexSwapPosition p = new OkexSwapPosition();
        p.avail_position = BigDecimal.ZERO;
        p.avg_cost = BigDecimal.ZERO;
        p.instrument_id = "";
        p.last = BigDecimal.ZERO;
        p.leverage = BigDecimal.ZERO;
        p.liquidation_price = BigDecimal.ZERO;
        p.maint_margin_ratio = BigDecimal.ZERO;
        p.margin = BigDecimal.ZERO;
        p.position = BigDecimal.ZERO;
        p.realized_pnl = BigDecimal.ZERO;
        p.settled_pnl = BigDecimal.ZERO;
        p.settlement_price = BigDecimal.ZERO;
        p.side = "";
        p.timestamp = null;
        p.unrealized_pnl = BigDecimal.ZERO;
        return p;
    }
}
