package com.bitplay.market.model;

import com.bitplay.arbitrage.BestQuotes;
import com.bitplay.arbitrage.SignalType;

import org.knowm.xchange.dto.Order;

import java.math.BigDecimal;

/**
 * Created by Sergey Shurmin on 11/19/17.
 */
public class PlaceOrderArgs {
    final private Order.OrderType orderType;
    final private BigDecimal amount;
    final private BestQuotes bestQuotes;
    final private PlacingType placingType;
    final private SignalType signalType;
    final private int attempt;

    public PlaceOrderArgs(Order.OrderType orderType, BigDecimal amount, BestQuotes bestQuotes, PlacingType placingType, SignalType signalType, int attempt) {
        this.orderType = orderType;
        this.amount = amount;
        this.bestQuotes = bestQuotes;
        this.placingType = placingType;
        this.signalType = signalType;
        this.attempt = attempt;
    }

    public static PlaceOrderArgs nextPlacingArgs(PlaceOrderArgs curr) {
        return new PlaceOrderArgs(curr.orderType, curr.amount, curr.bestQuotes, curr.placingType, curr.signalType, curr.attempt + 1);
    }

    public Order.OrderType getOrderType() {
        return orderType;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public BestQuotes getBestQuotes() {
        return bestQuotes;
    }

    public PlacingType getPlacingType() {
        return placingType;
    }

    public SignalType getSignalType() {
        return signalType;
    }

    public int getAttempt() {
        return attempt;
    }
}
