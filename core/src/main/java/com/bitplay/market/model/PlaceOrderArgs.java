package com.bitplay.market.model;

import com.bitplay.arbitrage.dto.BestQuotes;
import com.bitplay.arbitrage.dto.SignalType;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.ToString;
import org.knowm.xchange.dto.Order;
import org.knowm.xchange.dto.Order.OrderType;

/**
 * Created by Sergey Shurmin on 11/19/17.
 */
@AllArgsConstructor
@Getter
@ToString
public class PlaceOrderArgs {

    final private Order.OrderType orderType;
    final private BigDecimal amount;
    final private BestQuotes bestQuotes;
    final private PlacingType placingType;
    final private SignalType signalType;
    final private int attempt;
    final private String counterName;
    final private Instant lastObTime;

    public PlaceOrderArgs(OrderType orderType, BigDecimal amount, BestQuotes bestQuotes, PlacingType placingType,
            SignalType signalType, int attempt, String counterName) {
        this.orderType = orderType;
        this.amount = amount;
        this.bestQuotes = bestQuotes;
        this.placingType = placingType;
        this.signalType = signalType;
        this.attempt = attempt;
        this.counterName = new String(counterName);
        this.lastObTime = null;
    }

    public static PlaceOrderArgs nextPlacingArgs(PlaceOrderArgs curr) {
        return new PlaceOrderArgs(curr.orderType, curr.amount, curr.bestQuotes, curr.placingType, curr.signalType,
                curr.attempt + 1, curr.counterName, curr.lastObTime);
    }

}
