package com.bitplay.market.model;

import com.bitplay.arbitrage.dto.BestQuotes;
import com.bitplay.arbitrage.dto.SignalType;
import com.bitplay.persistance.domain.settings.AmountType;
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
    final private AmountType amountType;
    final private Instant preliqQueuedTime;
    final private String preliqMarketName;
    private boolean pricePlanOnStart = false;

    public PlaceOrderArgs(OrderType orderType, BigDecimal amount, BestQuotes bestQuotes, PlacingType placingType, SignalType signalType, int attempt,
            Long tradeId, String counterName, Instant lastObTime, ContractType contractType, AmountType amountType, Instant preliqQueuedTime,
            String preliqMarketName) {
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
        this.amountType = amountType;
        this.preliqQueuedTime = preliqQueuedTime;
        this.preliqMarketName = preliqMarketName;
    }

    public PlaceOrderArgs(OrderType orderType, BigDecimal amount, BestQuotes bestQuotes, PlacingType placingType, SignalType signalType, int attempt,
            Long tradeId, String counterName, Instant lastObTime, ContractType contractType, AmountType amountType) {
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
        this.amountType = amountType;
        this.preliqQueuedTime = null;
        this.preliqMarketName = null;
    }

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
        this.amountType = null;
        this.preliqQueuedTime = null;
        this.preliqMarketName = null;
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
        this.amountType = null;
        this.preliqQueuedTime = null;
        this.preliqMarketName = null;
    }

    public PlaceOrderArgs(OrderType orderType, BigDecimal amount, BestQuotes bestQuotes, PlacingType placingType,
            SignalType signalType, int attempt, Long tradeId, String counterName) {
        this.orderType = orderType;
        this.amount = amount;
        this.bestQuotes = bestQuotes;
        this.placingType = placingType;
        this.signalType = signalType;
        this.attempt = attempt;
        this.tradeId = tradeId;
        this.counterName = counterName;
        this.lastObTime = null;
        this.contractType = null;
        this.amountType = null;
        this.preliqQueuedTime = null;
        this.preliqMarketName = null;
    }

    public static PlaceOrderArgs nextPlacingArgs(PlaceOrderArgs curr) {
        return new PlaceOrderArgs(curr.orderType, curr.amount, curr.bestQuotes, curr.placingType, curr.signalType,
                curr.attempt + 1, curr.tradeId, curr.counterName, curr.lastObTime, curr.contractType, curr.amountType, curr.preliqQueuedTime,
                curr.preliqMarketName);
    }

    public PlaceOrderArgs cloneWithPlacingType(PlacingType placingType) {
        return new PlaceOrderArgs(this.orderType, this.amount, this.bestQuotes, placingType, this.signalType, this.attempt, this.tradeId, this.counterName,
                this.lastObTime, this.contractType, this.amountType, this.preliqQueuedTime, this.preliqMarketName);
    }

    public PlaceOrderArgs cloneWithAmount(BigDecimal amount) {
        return new PlaceOrderArgs(this.orderType, amount, this.bestQuotes, this.placingType, this.signalType, this.attempt, this.tradeId, this.counterName,
                this.lastObTime, this.contractType, this.amountType, this.preliqQueuedTime,
                this.preliqMarketName);
    }

    public void setPricePlanOnStart(boolean pricePlanOnStart) {
        this.pricePlanOnStart = pricePlanOnStart;
    }
}
