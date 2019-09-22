package com.bitplay.okex.v3.dto.futures.param;

import com.bitplay.okex.v3.enums.FuturesDirectionEnum;
import lombok.Data;

@Data
public class ClosePosition {
    /**
     * The id of the futures, eg: BTC-USD-180629
     */
    private final String instrument_id;
    /**
     * The execution type {@link FuturesDirectionEnum}
     */
    private final FuturesDirectionEnum direction;

}
