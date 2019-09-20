package com.bitplay.okex.v3.dto.futures.result;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class OrderResult {

    /**
     * You setting order id.
     */
    private String client_oid;
    /**
     * The order id provided by OKEx.
     */
    private String order_id;
    /**
     * The Server processing results: true: successful, false: failure.
     */
    private boolean result;

    private String error_code;
    private String error_message;

    // create default result
    public OrderResult(boolean result, String order_id) {
        this.result = result;
        this.order_id = order_id;
        this.error_code = "0";
    }
}
