package com.bitplay.okex.v5.dto.param;

import java.math.BigDecimal;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class Order {

    /**
     * The id of the futures, eg: BTC-USD-180629
     */
    protected String instId;
    /**
     * lever, default 10.
     */
//    protected BigDecimal leverage;
    /**
     * You setting order id.(optional)
     */
    private String clOrdId;

    private final String tdMode = "cross";

    /**
     * Order side, buy sell
     */
    private String side;

    /**
     * The order price: Maximum 1 million
     */
    private BigDecimal px;
    /**
     * The order amount: Maximum 1 million
     */
    private Integer sz;
//    /**
//     * Match best counter party price (BBO)? 0: No 1: Yes   If yes, the 'price' field is ignored
//     */
//    private Integer match_price;

    // Whether to reduce position only or not, true false, the default is false.
    //Only applicable to MARGIN orders
    //private Boolean reduceOnly;
    /**
     * Order type
     * market: market order
     * limit: limit order
     * post_only: Post-only order
     * fok: Fill-or-kill order
     * ioc: Immediate-or-cancel order
     * optimal_limit_ioc :Market order with immediate-or-cancel order (applicable only to Futures and Perpetual swap).
     */
    private String ordType;


}
