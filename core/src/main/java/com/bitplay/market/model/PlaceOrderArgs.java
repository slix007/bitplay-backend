package com.bitplay.market.model;

import com.bitplay.arbitrage.dto.BestQuotes;
import com.bitplay.arbitrage.dto.SignalType;
import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.ToString;
import org.knowm.xchange.dto.Order;

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

    public static PlaceOrderArgs nextPlacingArgs(PlaceOrderArgs curr) {
        return new PlaceOrderArgs(curr.orderType, curr.amount, curr.bestQuotes, curr.placingType, curr.signalType, curr.attempt + 1, curr.counterName);
    }

}
