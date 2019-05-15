package com.bitplay.market.model;

import com.bitplay.arbitrage.dto.BestQuotes;
import com.bitplay.arbitrage.dto.SignalType;
import com.bitplay.persistance.domain.settings.AmountType;
import com.bitplay.persistance.domain.settings.ContractType;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.knowm.xchange.dto.Order;
import org.knowm.xchange.dto.Order.OrderType;
import org.springframework.data.annotation.Transient;

/**
 * Created by Sergey Shurmin on 11/19/17.
 */
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Getter
@ToString
public class PlaceOrderArgs {

    @Transient
    public static final int NO_REPEATS_ATTEMPT = -1;

    private Order.OrderType orderType;
    private BigDecimal amount;
    private BestQuotes bestQuotes;
    private PlacingType placingType;
    private SignalType signalType;
    private int attempt;
    private Long tradeId;
    private String counterName;
    private Instant lastObTime;
    private ContractType contractType;
    private AmountType amountType;
    private Instant preliqQueuedTime;
    private String preliqMarketName;
    private boolean pricePlanOnStart = false;
    private boolean preliqOrder = false;
    private Integer portionsQty;
    private Integer portionsQtyMax;

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

    public static PlaceOrderArgs nextPlacingArgs(PlaceOrderArgs curr) {
        return new PlaceOrderArgs(curr.orderType, curr.amount, curr.bestQuotes, curr.placingType, curr.signalType,
                curr.attempt + 1, curr.tradeId, curr.counterName, curr.lastObTime, curr.contractType, curr.amountType, curr.preliqQueuedTime,
                curr.preliqMarketName, curr.pricePlanOnStart, curr.preliqOrder, curr.portionsQty, curr.portionsQtyMax);
    }

    public PlaceOrderArgs cloneWithPlacingType(PlacingType placingType) {
        return new PlaceOrderArgs(this.orderType, this.amount, this.bestQuotes, placingType, this.signalType, this.attempt, this.tradeId, this.counterName,
                this.lastObTime, this.contractType, this.amountType, this.preliqQueuedTime, this.preliqMarketName,
                this.pricePlanOnStart, this.preliqOrder, this.portionsQty, this.portionsQtyMax);
    }

    public PlaceOrderArgs cloneWithAmount(BigDecimal amount) {
        return new PlaceOrderArgs(this.orderType, amount, this.bestQuotes, this.placingType, this.signalType, this.attempt, this.tradeId, this.counterName,
                this.lastObTime, this.contractType, this.amountType, this.preliqQueuedTime,
                this.preliqMarketName, this.pricePlanOnStart, this.preliqOrder, this.portionsQty, this.portionsQtyMax);
    }

    public void setPricePlanOnStart(boolean pricePlanOnStart) {
        this.pricePlanOnStart = pricePlanOnStart;
    }

    public void setPreliqOrder(boolean preliqOrder) {
        this.preliqOrder = preliqOrder;
    }

    public String getCounterNameWithPortion() {
        if (portionsQty == null || portionsQtyMax == null) {
            return counterName;
        }
        return String.format("%s_portion_%s/%s", counterName, portionsQty, portionsQtyMax);
    }
}
