package com.bitplay.persistance.domain.fluent;

import com.bitplay.arbitrage.dto.BestQuotes;
import com.bitplay.arbitrage.dto.SignalType;
import com.bitplay.persistance.domain.settings.PlacingType;
import javax.validation.constraints.NotNull;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.knowm.xchange.dto.Order;
import org.knowm.xchange.dto.Order.OrderStatus;
import org.knowm.xchange.dto.trade.LimitOrder;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.TypeAlias;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Created by Sergey Shurmin on 12/20/17.
 */
@Document(collection = "ordersCollection")
@TypeAlias("orders")
@Data
@NoArgsConstructor
public class FplayOrder {

    @NotNull
    private String counterName;
    @Id
    private String orderId;
    private OrderDetail orderDetail; //NotNull
    @Version
    private Long version;
//    @CreatedDate
//    private LocalDateTime creationDate;
//    @LastModifiedDate
//    private LocalDateTime lastChange;
    private BestQuotes bestQuotes;
    private PlacingType placingType;
    private SignalType signalType;
    private Integer portionsQty;
    private Integer portionsQtyMax;

    private Long tradeId;

    public FplayOrder(Long tradeId, String counterName) {
        this.tradeId = tradeId;
        this.counterName = counterName;
    }

    public FplayOrder(Long tradeId, String counterName, @NotNull Order order, BestQuotes bestQuotes, PlacingType placingType, SignalType signalType) {
        this.tradeId = tradeId;
        this.counterName = counterName;
        this.orderId = order.getId();
        this.orderDetail = FplayOrderConverter.convert(order);
        this.bestQuotes = bestQuotes;
        this.placingType = placingType;
        this.signalType = signalType;
    }

    public FplayOrder(Long tradeId, String counterName, Order order, BestQuotes bestQuotes, PlacingType placingType, SignalType signalType,
            Integer portionsQty, Integer portionsQtyMax) {
        this.tradeId = tradeId;
        this.counterName = counterName;
        this.orderId = order != null ? order.getId() : null;
        this.orderDetail = FplayOrderConverter.convert(order);
        this.bestQuotes = bestQuotes;
        this.placingType = placingType;
        this.signalType = signalType;
        this.portionsQty = portionsQty;
        this.portionsQtyMax = portionsQtyMax;
    }

    public FplayOrder cloneWithUpdate(LimitOrder limitOrder) {
        return new FplayOrder(this.tradeId, this.counterName, limitOrder, this.bestQuotes, this.placingType, this.signalType, this.portionsQty,
                this.portionsQtyMax);
    }

    public FplayOrder cloneDeep() {
        return new FplayOrder(this.tradeId, this.counterName, this.getLimitOrder(), this.bestQuotes, this.placingType, this.signalType, this.portionsQty,
                this.portionsQtyMax);
    }

    public Order getOrder() {
        return FplayOrderConverter.convert(orderDetail);
    }

    public LimitOrder getLimitOrder() {
        return FplayOrderConverter.convert(orderDetail);
    }

    public boolean isOpen() {
        if (orderDetail == null) { // todo should not be null
            return false;
        }
        return isOpen(orderDetail.getOrderStatus());
    }

    public static boolean isOpen(OrderStatus orderStatus) {
        return orderStatus == Order.OrderStatus.NEW
                || orderStatus == Order.OrderStatus.PARTIALLY_FILLED
                || orderStatus == Order.OrderStatus.PENDING_NEW
                || orderStatus == Order.OrderStatus.PENDING_CANCEL
                || orderStatus == Order.OrderStatus.PENDING_REPLACE;
    }

    public boolean isEth() {
        return orderDetail.getContractType().contains("ETH");
    }

    public String getPortionsStr() {
        if (portionsQty == null || portionsQtyMax == null) {
            return "0/0";
        }
        return String.format("%s/%s", portionsQty, portionsQtyMax);
    }

    public String getCounterWithPortion() {
        if (portionsQty == null || portionsQtyMax == null) {
            return counterName;
        }
        return String.format("%s_portion_%s/%s", counterName, portionsQty, portionsQtyMax);
    }
}
