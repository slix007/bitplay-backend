package com.bitplay.okex.v3.dto.futures.result;

import java.math.BigDecimal;
import java.util.Date;
import lombok.Data;

@Data
public class EstimatedPrice {
    /**
     * The id of the futures contract
     */
    private String instrument_id;
    /**
     * Estimated price
     */
    private BigDecimal settlement_price;
    /**
     * time
     */
    private Date timestamp;

}
