package com.bitplay.okex.v5.dto.result;

// Return Sample
//{
//    "BTC-USD-190322":{
//        "long_leverage":"10",
//        "short_leverage":"10"
//    },
//    "margin_mode":"fixed",
//    "BTC-USD-190628":{
//        "long_leverage":"10",
//        "short_leverage":"10"
//    },
//    "BTC-USD-190329":{
//        "long_leverage":"10",
//        "short_leverage":"10"
//    }
//}

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import lombok.Data;

/**
 * <p> margin_mode=crossed: currency, leverage </p>
 * <p> margin_mode=fixed: instrument_id, long_leverage, short_leverage </p>
 */
@Data
public class LeverageResult {

    List<LeverageResultData> data;

    public LeverageResultData getOne() {
        return data != null && data.size() > 0
                ? data.get(0)
                : null;
    }

    @Data
    public static class LeverageResultData {
        /**
         * cross isolated
         */
        private String mgnMode;
        private String instId;
        // we use only net
        private String posSide;


        private BigDecimal lever;

        /**
         * Used in change request.
         */
        private String result;

    }
}
