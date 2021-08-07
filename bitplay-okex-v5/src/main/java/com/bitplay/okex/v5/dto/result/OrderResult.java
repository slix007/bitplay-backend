package com.bitplay.okex.v5.dto.result;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class OrderResult {

    List<OrderResultData> data;

    @Data
    public static class OrderResultData {

        /**
         * You setting order id.
         */
        private String clOrdId;
        /**
         * The order id provided by OKEx.
         */
        private String ordId;
        /**
         * The code of the event execution result, 0 means success..
         */
        private String sCode;

        /**
         * Message shown when the event execution fails.
         */
        private String sMsg;
    }

}
