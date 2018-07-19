package com.bitplay.persistance.domain.fluent;

import com.bitplay.arbitrage.dto.BestQuotes;
import com.bitplay.arbitrage.dto.SignalType;
import com.bitplay.market.model.PlacingType;
import javax.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.knowm.xchange.dto.Order;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.TypeAlias;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Created by Sergey Shurmin on 12/20/17.
 */
@Document(collection = "ordersCollection")
@TypeAlias("orders")
@Getter
@Setter
@ToString
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

    public FplayOrder(String counterName) {
        this.counterName = counterName;
    }

    public FplayOrder(String counterName, @NotNull Order order, BestQuotes bestQuotes, PlacingType placingType, SignalType signalType) {
        this.counterName = counterName;
        this.orderId = order.getId();
        this.orderDetail = FplayOrderConverter.convert(order);
        this.bestQuotes = bestQuotes;
        this.placingType = placingType;
        this.signalType = signalType;
    }

    public Order getOrder() {
        return FplayOrderConverter.convert(orderDetail);
    }

    public void setOrder(Order order) {
        this.orderDetail = FplayOrderConverter.convert(order);
    }

}
