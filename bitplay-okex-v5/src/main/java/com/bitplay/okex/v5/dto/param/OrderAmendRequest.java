package com.bitplay.okex.v5.dto.param;

import java.math.BigDecimal;
import lombok.Data;

@Data
public class OrderAmendRequest {

    private final String instId;
//    /**
//     * Whether the order needs to be automatically canceled when the order amendment fails
//     * false or true, the default is false.
//     * Optional.
//     */
//    private final Boolean cxlOnFail = false;
    /**
     * Order ID Either ordId or clOrdId is required. If both are passed, ordId will be used.
     */
    private final String ordId;

    /**
     * New price after amendment.
     * Optional.
     */
    private final BigDecimal newPx;

//    /**
//     * New quantity after amendment. Either newSz or newPx is required. When amending a partially-filled order, the newSz should include the amount that has been filled.
//     * Optional.
//     */
//    private final BigDecimal newSz;
}
