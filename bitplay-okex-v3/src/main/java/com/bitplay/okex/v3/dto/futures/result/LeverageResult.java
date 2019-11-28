package com.bitplay.okex.v3.dto.futures.result;

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

import lombok.Data;

import java.math.BigDecimal;

/**
 * <p> margin_mode=crossed: currency, leverage </p>
 * <p> margin_mode=fixed: instrument_id, long_leverage, short_leverage </p>
 */
@Data
public class LeverageResult {

    /**
     * crossed, fixed
     */
    private String margin_mode;

    private String currency;
    private String leverage;

    /**
     * Used in change request.
     */
    private String result;

    // crossed:
    // {
    //    "long_leverage":"10.0000",
    //    "short_leverage":"10.0000",
    //    "margin_mode":"crossed",
    //    "instrument_id":"BTC-USD-SWAP"
    //}

    /**
     * SWAP
     */
    private String instrument_id;

    private BigDecimal long_leverage;
    private BigDecimal short_leverage;
}
