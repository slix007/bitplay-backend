package com.bitplay.market.model;

import com.bitplay.arbitrage.dto.BestQuotes;
import com.bitplay.arbitrage.dto.SignalType;
import com.bitplay.persistance.domain.settings.ContractType;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.Getter;
import lombok.ToString;
import org.knowm.xchange.dto.Order;
import org.knowm.xchange.dto.Order.OrderType;

/**
 * Created by Sergey Shurmin on 11/19/17.
 */
//@AllArgsConstructor
@Getter
@ToString
public class PlaceOrderArgs {

    final private Order.OrderType orderType;
    final private BigDecimal amount;
    final private BestQuotes bestQuotes;
    final private PlacingType placingType;
    final private SignalType signalType;
    final private int attempt;
    final private Long tradeId;
    final private String counterName;
    final private Instant lastObTime;
    final private ContractType contractType;

    public PlaceOrderArgs(OrderType orderType, BigDecimal amount, BestQuotes bestQuotes, PlacingType placingType, SignalType signalType, int attempt,
            Long tradeId, String counterName, Instant lastObTime, ContractType contractType) {
        this.orderType = orderType;
        this.amount = amount;
        this.bestQuotes = bestQuotes;
        this.placingType = placingType;
        this.signalType = signalType;
        this.attempt = attempt;
        this.counterName = counterName;
        this.tradeId = tradeId;
        this.lastObTime = lastObTime;
        this.contractType = contractType;
    }

    public PlaceOrderArgs(OrderType orderType, BigDecimal amount, BestQuotes bestQuotes, PlacingType placingType, SignalType signalType, int attempt,
            Long tradeId, String counterName, Instant lastObTime) {
        this.orderType = orderType;
        this.amount = amount;
        this.bestQuotes = bestQuotes;
        this.placingType = placingType;
        this.signalType = signalType;
        this.attempt = attempt;
        this.counterName = counterName;
        this.tradeId = tradeId;
        this.lastObTime = lastObTime;
        this.contractType = null;
    }

    public PlaceOrderArgs(OrderType orderType, BigDecimal amount, BestQuotes bestQuotes, PlacingType placingType,
            SignalType signalType, int attempt, Long tradeId, String counterName) {
        this.orderType = orderType;
        this.amount = amount;
        this.bestQuotes = bestQuotes;
        this.placingType = placingType;
        this.signalType = signalType;
        this.attempt = attempt;
        this.tradeId = tradeId == null ? null : tradeId.longValue();
        this.counterName = counterName;
        this.lastObTime = null;
        this.contractType = null;
    }

    public static PlaceOrderArgs nextPlacingArgs(PlaceOrderArgs curr) {
        return new PlaceOrderArgs(curr.orderType, curr.amount, curr.bestQuotes, curr.placingType, curr.signalType,
                curr.attempt + 1, curr.tradeId, curr.counterName, curr.lastObTime, curr.contractType);
    }

}
