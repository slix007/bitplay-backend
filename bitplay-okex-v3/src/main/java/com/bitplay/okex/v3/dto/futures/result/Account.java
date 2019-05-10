package com.bitplay.okex.v3.dto.futures.result;

import java.math.BigDecimal;
import lombok.Data;

@Data
public class Account {

    private BigDecimal equity;
    private BigDecimal margin;
    private BigDecimal margin_for_unfilled;
    private BigDecimal margin_frozen;
    private String margin_mode; //crossed,
    private BigDecimal margin_ratio;
    private BigDecimal realized_pnl;
    private BigDecimal total_avail_balance;
    private BigDecimal unrealized_pnl;

    @Override
    public String toString() {
        return "Account" +
                "{\n equity=" + equity +
                ",\n margin=" + margin +
                ",\n margin_for_unfilled=" + margin_for_unfilled +
                ",\n margin_frozen=" + margin_frozen +
                ",\n margin_mode='" + margin_mode + '\'' +
                ",\n margin_ratio=" + margin_ratio +
                ",\n realized_pnl=" + realized_pnl +
                ",\n total_avail_balance=" + total_avail_balance +
                ",\n unrealized_pnl=" + unrealized_pnl +
                "}\n";
    }
}
