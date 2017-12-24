package com.bitplay.persistance.domain.fluent;

import com.bitplay.arbitrage.BestQuotes;
import com.bitplay.arbitrage.SignalType;
import com.bitplay.market.model.PlacingType;

import org.knowm.xchange.dto.Order;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.TypeAlias;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.mapping.Document;

import javax.validation.constraints.NotNull;

/**
 * Created by Sergey Shurmin on 12/20/17.
 */
@Document(collection = "ordersCollection")
@TypeAlias("orders")
public class FplayOrder {

    @Id
    private String orderId;
    @NotNull
    private OrderDetail orderDetail;

    @Version
    private Long version;
//    @CreatedDate
//    private LocalDateTime creationDate;
//    @LastModifiedDate
//    private LocalDateTime lastChange;

    private BestQuotes bestQuotes;
    private PlacingType placingType;
    private SignalType signalType;

    public FplayOrder() {
    }

    public FplayOrder(@NotNull Order order, BestQuotes bestQuotes, PlacingType placingType, SignalType signalType) {
        this.orderId = order.getId();
        this.orderDetail = FplayOrderConverter.convert(order);
        this.bestQuotes = bestQuotes;
        this.placingType = placingType;
        this.signalType = signalType;
    }

    public String getOrderId() {
        return orderId;
    }

    public void setOrderId(String orderId) {
        this.orderId = orderId;
    }

    public OrderDetail getOrderDetail() {
        return orderDetail;
    }

    public void setOrderDetail(OrderDetail orderDetail) {
        this.orderDetail = orderDetail;
    }

    public Order getOrder() {
        return FplayOrderConverter.convert(orderDetail);
    }

    public void setOrder(Order order) {
        this.orderDetail = FplayOrderConverter.convert(order);
    }

    public BestQuotes getBestQuotes() {
        return bestQuotes;
    }

    public void setBestQuotes(BestQuotes bestQuotes) {
        this.bestQuotes = bestQuotes;
    }

    public PlacingType getPlacingType() {
        return placingType;
    }

    public void setPlacingType(PlacingType placingType) {
        this.placingType = placingType;
    }

    public SignalType getSignalType() {
        return signalType;
    }

    public void setSignalType(SignalType signalType) {
        this.signalType = signalType;
    }

    @Override
    public String toString() {
        return "FplayOrder{" +
                "orderId='" + orderId + '\'' +
                ", orderDetail=" + orderDetail +
                ", version=" + version +
                ", bestQuotes=" + bestQuotes +
                ", placingType=" + placingType +
                ", signalType=" + signalType +
                '}';
    }
}
