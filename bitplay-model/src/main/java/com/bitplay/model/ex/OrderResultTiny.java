package com.bitplay.model.ex;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class OrderResultTiny {

    private String client_oid;
    private String order_id;
    /**
     * The Server processing results: true: successful, false: failure.
     */
    private boolean result;

    private String error_code;
    private String error_message;

    // create default result
    public OrderResultTiny(boolean result, String order_id) {
        this.result = result;
        this.order_id = order_id;
        this.error_code = "0";
    }
}
