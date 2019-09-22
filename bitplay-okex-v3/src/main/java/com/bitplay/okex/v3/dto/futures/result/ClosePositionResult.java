package com.bitplay.okex.v3.dto.futures.result;

import com.bitplay.okex.v3.enums.FuturesDirectionEnum;
import lombok.Data;

@Data
public class ClosePositionResult {
    /**
     * The id of the futures, eg: BTC-USD-180629
     */
    private String instrument_id;
    /**
     * The execution type {@link FuturesDirectionEnum}
     */
    private FuturesDirectionEnum direction;

    private Integer error_code;
    private String error_message;
    private Boolean result;
}
