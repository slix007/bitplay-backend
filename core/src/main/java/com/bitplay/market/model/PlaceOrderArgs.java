package com.bitplay.market.model;

import com.bitplay.arbitrage.dto.BestQuotes;
import com.bitplay.arbitrage.dto.SignalType;
import com.bitplay.persistance.domain.fluent.DeltaName;
import com.bitplay.persistance.domain.settings.AmountType;
import com.bitplay.persistance.domain.settings.ArbScheme;
import com.bitplay.persistance.domain.settings.ContractType;
import com.bitplay.persistance.domain.settings.PlacingType;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.knowm.xchange.dto.Order;
import org.springframework.data.annotation.Transient;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Created by Sergey Shurmin on 11/19/17.
 */
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
@Getter
@ToString
public class PlaceOrderArgs {

    @Transient
    public static final int NO_REPEATS_ATTEMPT = -1;

    private Order.OrderType orderType;
    private BigDecimal fullAmount; // need for portions
    private BigDecimal amount;
    private BestQuotes bestQuotes;
    private PlacingType placingType;
    private SignalType signalType;
    private int attempt;
    private Long tradeId;
    private String counterName;
    private PlBefore beforeSignalMetrics = new PlBefore();
    private ContractType contractType;
    private AmountType amountType;
    private Instant preliqQueuedTime;
    private String preliqMarketName;
    private boolean pricePlanOnStart = false;
    private boolean preliqOrder = false;
    private Integer portionsQty;    // portion number in a row
    private Integer portionsQtyMax; // amount of portions in a row
    private BtmFokAutoArgs btmFokArgs;
    private ArbScheme arbScheme;

    public static PlaceOrderArgs nextPlacingArgs(PlaceOrderArgs curr) {
        return new PlaceOrderArgs(curr.orderType, curr.fullAmount, curr.amount, curr.bestQuotes, curr.placingType, curr.signalType,
                curr.attempt + 1, curr.tradeId, curr.counterName, curr.beforeSignalMetrics, curr.contractType, curr.amountType, curr.preliqQueuedTime,
                curr.preliqMarketName, curr.pricePlanOnStart, curr.preliqOrder, curr.portionsQty, curr.portionsQtyMax, curr.btmFokArgs, curr.arbScheme);
    }

    public PlaceOrderArgs cloneWithPlacingType(PlacingType placingType) {
        return new PlaceOrderArgs(this.orderType, this.fullAmount, this.amount, this.bestQuotes, placingType, this.signalType, this.attempt, this.tradeId,
                this.counterName,
                this.beforeSignalMetrics, this.contractType, this.amountType, this.preliqQueuedTime, this.preliqMarketName,
                this.pricePlanOnStart, this.preliqOrder, this.portionsQty, this.portionsQtyMax, this.btmFokArgs, this.arbScheme);
    }

    public PlaceOrderArgs cloneWithAmount(BigDecimal amount) {
        return new PlaceOrderArgs(this.orderType, amount, amount, this.bestQuotes, this.placingType, this.signalType, this.attempt, this.tradeId,
                this.counterName,
                this.beforeSignalMetrics, this.contractType, this.amountType, this.preliqQueuedTime,
                this.preliqMarketName, this.pricePlanOnStart, this.preliqOrder, this.portionsQty, this.portionsQtyMax, this.btmFokArgs, this.arbScheme);
    }

    public PlaceOrderArgs cloneWithFullAmount(BigDecimal newFullAmount) {
        final BigDecimal amount;
        if (this.fullAmount.compareTo(newFullAmount) == 0) {
            amount = this.amount;
        } else {
            final BigDecimal filled = this.fullAmount.subtract(this.amount);
            amount = newFullAmount.subtract(filled);
        }
        return new PlaceOrderArgs(this.orderType, newFullAmount, amount, this.bestQuotes, this.placingType, this.signalType, this.attempt, this.tradeId,
                this.counterName,
                this.beforeSignalMetrics, this.contractType, this.amountType, this.preliqQueuedTime,
                this.preliqMarketName, this.pricePlanOnStart, this.preliqOrder, this.portionsQty, this.portionsQtyMax, this.btmFokArgs, this.arbScheme);
    }

    public PlaceOrderArgs cloneWithAmountAndPortionsQty(BigDecimal amount, Integer portionsQty) {
        return new PlaceOrderArgs(this.orderType, this.fullAmount, amount, this.bestQuotes, this.placingType, this.signalType, this.attempt, this.tradeId,
                this.counterName,
                this.beforeSignalMetrics, this.contractType, this.amountType, this.preliqQueuedTime,
                this.preliqMarketName, this.pricePlanOnStart, this.preliqOrder, portionsQty, this.portionsQtyMax, this.btmFokArgs, this.arbScheme);
    }

    public PlaceOrderArgs cloneAsPortion(BigDecimal amount) {
        int portionsQty = this.portionsQty != null ? this.portionsQty + 1 : 1;

        return new PlaceOrderArgs(this.orderType, this.fullAmount, amount, this.bestQuotes, this.placingType, this.signalType, this.attempt, this.tradeId,
                this.counterName,
                this.beforeSignalMetrics, this.contractType, this.amountType, this.preliqQueuedTime,
                this.preliqMarketName, this.pricePlanOnStart, this.preliqOrder, portionsQty, null, this.btmFokArgs, this.arbScheme);
    }

    public void setPricePlanOnStart(boolean pricePlanOnStart) {
        this.pricePlanOnStart = pricePlanOnStart;
    }

    public void setPreliqOrder(boolean preliqOrder) {
        this.preliqOrder = preliqOrder;
    }

    public String getCounterNameWithPortion() {
        if (portionsQty == null) {
            return counterName;
        }
        if (portionsQtyMax == null) {
            return String.format("%s_p%s", counterName, portionsQty);
        }
        return String.format("%s_p%s/%s", counterName, portionsQty, portionsQtyMax);
    }

    public void setBtmFokArgs(BtmFokAutoArgs btmFokArgs) {
        this.btmFokArgs = btmFokArgs;
    }

    public void setPortionsQty(Integer portionsQty) {
        this.portionsQty = portionsQty;
    }

    public DeltaName getDeltaName() {
        final Order.OrderType t = getOrderType();
        final boolean okexBuy = t == Order.OrderType.BID || t == Order.OrderType.EXIT_ASK;
        return okexBuy ? DeltaName.B_DELTA : DeltaName.O_DELTA;
    }

}
