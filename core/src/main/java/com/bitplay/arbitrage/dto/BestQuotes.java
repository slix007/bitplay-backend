package com.bitplay.arbitrage.dto;

import java.math.BigDecimal;

/**
 * Created by Sergey Shurmin on 4/27/17.
 */
public class BestQuotes {

    private BigDecimal ask1_o = BigDecimal.ZERO;
    private BigDecimal ask1_p = BigDecimal.ZERO;
    private BigDecimal bid1_o = BigDecimal.ZERO;
    private BigDecimal bid1_p = BigDecimal.ZERO;
    private ArbitrageEvent arbitrageEvent = ArbitrageEvent.NONE;
    public BestQuotes(BigDecimal ask1_o, BigDecimal ask1_p, BigDecimal bid1_o, BigDecimal bid1_p) {
        this.ask1_o = ask1_o;
        this.ask1_p = ask1_p;
        this.bid1_o = bid1_o;
        this.bid1_p = bid1_p;
    }

    public static BestQuotes empty() {
        return new BestQuotes(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
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

    public ArbitrageEvent getArbitrageEvent() {
        return arbitrageEvent;
    }

    public void setArbitrageEvent(ArbitrageEvent arbitrageEvent) {
        this.arbitrageEvent = arbitrageEvent;
    }

    public boolean hasEmpty() {
        return ask1_o == null || ask1_o.signum() == 0
                && ask1_p == null || ask1_p.signum() == 0
                && bid1_o == null || bid1_o.signum() == 0
                && bid1_p == null || bid1_p.signum() == 0;
    }

    @Override
    public String toString() {
        return "BestQuotes{" +
                "ask1_o=" + ask1_o +
                ", ask1_p=" + ask1_p +
                ", bid1_o=" + bid1_o +
                ", bid1_p=" + bid1_p +
                ", arbitrageEvent=" + arbitrageEvent +
                '}';
    }

    public enum ArbitrageEvent {
        NONE,
        ONLY_SIGNAL,
        TRADE_STARTED
    }
}