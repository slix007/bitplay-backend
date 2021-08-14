package com.bitplay.okex.v5.dto.result;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.Data;

@Data
public class OrderResult {

    // {code=1, data=[{clOrdId=, ordId=, sCode=51119, sMsg=Order placement failed due to insufficient balance. , tag=}], msg=}
    private String code;
    private String msg;
    private List<OrderResultData> data = new ArrayList<>();

    @Data
    public static class OrderResultData {

        /**
         * You setting order id.
         */
        private String tag;
        private String clOrdId;
        /**
         * The order id provided by OKEx.
         */
        private String ordId;
        /**
         * The code of the event execution result, 0 means success..
         */
        @JsonProperty("sCode")
        private String sCode;

        /**
         * Message shown when the event execution fails.
         */
        @JsonProperty("sMsg")
        private String sMsg;



    }

}
