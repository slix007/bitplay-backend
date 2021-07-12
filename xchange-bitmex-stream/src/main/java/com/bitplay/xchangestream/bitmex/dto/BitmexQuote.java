package com.bitplay.xchangestream.bitmex.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.math.BigDecimal;
import java.util.Date;

@JsonIgnoreProperties(ignoreUnknown = true)
@Data
public class BitmexQuote {

    //      "timestamp": "2019-12-30T08:29:55.124Z",
    //      "symbol": "XBTUSD",
    //      "bidSize": 40983,
    //      "bidPrice": 7342,
    //      "askPrice": 7342.5,
    //      "askSize": 2379305
    private final Date timestamp;
    private final String symbol; //XBTUSD
    private final BigDecimal bidSize;
    private final BigDecimal askSize;
    private final BigDecimal bidPrice;
    private final BigDecimal askPrice;
}
