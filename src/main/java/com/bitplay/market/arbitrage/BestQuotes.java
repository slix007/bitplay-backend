package com.bitplay.market.arbitrage;

import java.math.BigDecimal;

/**
 * Created by Sergey Shurmin on 4/27/17.
 */
public class BestQuotes {

    BigDecimal ask1_o = BigDecimal.ZERO;
    BigDecimal ask1_p = BigDecimal.ZERO;
    BigDecimal bid1_o = BigDecimal.ZERO;
    BigDecimal bid1_p = BigDecimal.ZERO;

    public BestQuotes(BigDecimal ask1_o, BigDecimal ask1_p, BigDecimal bid1_o, BigDecimal bid1_p) {
        this.ask1_o = ask1_o;
        this.ask1_p = ask1_p;
        this.bid1_o = bid1_o;
        this.bid1_p = bid1_p;
    }

    public BigDecimal getAsk1_o() {
        return ask1_o;
    }

    public BigDecimal getAsk1_p() {
        return ask1_p;
    }

    public BigDecimal getBid1_o() {
        return bid1_o;
    }

    public BigDecimal getBid1_p() {
        return bid1_p;
    }
}
