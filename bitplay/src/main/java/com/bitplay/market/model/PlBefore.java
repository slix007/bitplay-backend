package com.bitplay.market.model;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.time.Instant;

/**
 * Created by Sergey Shurmin on 11/19/17.
 */
@Setter
@Getter
@ToString
public class PlBefore {

    private Instant createQuote;
    private Instant getQuote;
    private Instant saveQuote;
    private Instant signalCheck;
    private Instant signalTime;
    private Instant requestPlacing;
    private Instant marketTransactTime;
    private Instant getAnswerFromPlacing;

    public PlBefore() {
    }

}
