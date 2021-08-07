package com.bitplay.okex.v5.dto.result;

import java.math.BigDecimal;
import java.util.Date;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class OrderDetail {

    private String instType;
    /**
     * The id of the futures, eg: BTC-USD-180629
     */
    private String instId;


    private String ordId;

    /**
     * Your settings order id.(optional)
     */
    private String clOrdId;
    /**
     * Price
     */
    private BigDecimal px;
    private BigDecimal avgPx;
    /**
     * quantity in contracts
     */
    private BigDecimal sz;
    private BigDecimal pnl;
    /**
     * Order type
     * market: market order
     * limit: limit order
     * post_only: Post-only order
     * fok: Fill-or-kill order
     * ioc: Immediate-or-cancel order
     * optimal_limit_ioc :Market order with immediate-or-cancel order
     */
    private String ordType;

    private String side;
    private String posSide;
    private String tdMode;
    private String fee;
    //	Accumulated fill quantity
    private BigDecimal accFillSz;


    private Date uTime;
//    /**
//     * Type (1: open long 2: open short 3: close long 4: close short)
//     */
//    private String type;
//    /**
//     * Usd amount in one contract.
//     */
//    private BigDecimal contract_val;
//    /**
//     * lever, default 10.
//     */
//    protected BigDecimal leverage;
//
//    /**
//     * -2:Failed,-1:Canceled,0:Open ,1:Partially Filled, 2:Fully Filled,3:Submitting,4:Canceling）
//     */
    /**
     * live
     * partially_filled
     */
    private String state;

}
