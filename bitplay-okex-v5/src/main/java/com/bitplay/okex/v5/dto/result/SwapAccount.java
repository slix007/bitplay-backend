package com.bitplay.okex.v5.dto.result;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;

@Data
public class SwapAccount {

    private BigDecimal equity;
    private BigDecimal margin;
    private BigDecimal margin_for_unfilled;
    private BigDecimal margin_frozen;
    private String margin_mode; //crossed,
    private BigDecimal margin_ratio;
    private BigDecimal realized_pnl;
    private BigDecimal total_avail_balance;
    private BigDecimal unrealized_pnl;
    private String liqui_mode; //tier, - 	Liquidation mode: tier（partial), legacy (complete)
    private BigDecimal maint_margin_ratio; // 	Maintenance margin ratio
    private BigDecimal liqui_fee_rate; // 	forced-liquidation fee
    private BigDecimal can_withdraw; // 	transferrable amount
    // swap only
    private BigDecimal fixed_balance;
    private String instrument_id;
    private LocalDateTime timestamp;

    private BigDecimal max_withdraw;

}
