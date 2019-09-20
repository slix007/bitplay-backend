package com.bitplay.okex.v3.dto.futures.result;

import java.math.BigDecimal;
import java.util.Date;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class OrderDetail {

    /**
     * The id of the futures, eg: BTC-USD-180629
     */
    private String instrument_id;
    /**
     * quantity in contracts
     */
    private BigDecimal size;
    private Date timestamp;
    private BigDecimal filled_qty;
    private String fee;
    private String order_id;
    private BigDecimal price;
    private BigDecimal price_avg;
    /**
     * Type (1: open long 2: open short 3: close long 4: close short)
     */
    private String type;
    /**
     * Usd amount in one contract.
     */
    private BigDecimal contract_val;
    /**
     * lever, default 10.
     */
    protected BigDecimal leverage;
    /**
     * You setting order id.(optional)
     */
    private String client_oid;
    /**
     * Fill in String for parameter，0: Normal limit order (Unfilled and 0 represent normal limit order) 1: Post only 2: Fill Or Kill 3: Immediatel Or Cancel.
     */
    private String order_type;
    private String pnl;
    /**
     * -2:Failed,-1:Canceled,0:Open ,1:Partially Filled, 2:Fully Filled,3:Submitting,4:Canceling）
     */
    private String state;

}
