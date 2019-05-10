package com.bitplay.okex.v3.dto.futures.result;

import lombok.Data;

@Data
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

}
