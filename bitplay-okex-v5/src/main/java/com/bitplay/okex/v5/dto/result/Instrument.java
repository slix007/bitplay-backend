package com.bitplay.okex.v5.dto.result;

import lombok.Data;

@Data
public class Instrument {

    /**
     * The id of the futures contract
     */
    private String instrument_id;
    /**
     * Currency
     */
    private String underlying_index;
    /**
     * Quote currency
     */
    private String quote_currency;
    /**
     * Minimum amount: $
     */
    private String tick_size;
    /**
     * Unit price per contract
     */
    private String contract_val;
    /**
     * Effect of time
     */
    private String listing;
    /**
     * Settlement price
     */
    private String delivery;
    /**
     * Minimum amount: cont
     */
    private String trade_increment;


}
