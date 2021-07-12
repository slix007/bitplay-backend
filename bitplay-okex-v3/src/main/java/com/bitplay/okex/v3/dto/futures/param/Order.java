package com.bitplay.okex.v3.dto.futures.param;

import com.bitplay.okex.v3.enums.FuturesTransactionTypeEnum;
import java.math.BigDecimal;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class Order {

    /**
     * The id of the futures, eg: BTC-USD-180629
     */
    protected String instrument_id;
    /**
     * lever, default 10.
     */
    protected BigDecimal leverage;
    /**
     * You setting order id.(optional)
     */
    private String client_oid;
    /**
     * The execution type {@link FuturesTransactionTypeEnum}
     */
    private Integer type;
    /**
     * The order price: Maximum 1 million
     */
    private BigDecimal price;
    /**
     * The order amount: Maximum 1 million
     */
    private Integer size;
    /**
     * Match best counter party price (BBO)? 0: No 1: Yes   If yes, the 'price' field is ignored
     */
    private Integer match_price;

    /**
     * Fill in String for parameter，0: Normal limit order (Unfilled and 0 represent normal limit order) 1: Post only 2: Fill Or Kill 3: Immediatel Or Cancel.
     */
    private String order_type;


}
