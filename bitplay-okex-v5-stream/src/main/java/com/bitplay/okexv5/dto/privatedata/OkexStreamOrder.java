package com.bitplay.okexv5.dto.privatedata;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.util.Date;

/**
 * Created by Sergey Shurmin on 6/10/17.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class OkexStreamOrder {

    private BigDecimal leverage;
    private String clientOid;
    private BigDecimal size;
    private BigDecimal filledQty;
    private BigDecimal price;
    private BigDecimal fee;
//    private BigDecimal contractVal;
    private BigDecimal priceAvg;
    private String side;
//    private Integer orderType;
    private String instrumentId;
    private String orderId;
    private Date timestamp;
    /**
     * Order status
     * live: to be effective
     * effective: effective
     * canceled: canceled
     * order_failed: order failed
     */
    private String state;

    public OkexStreamOrder(
            @JsonProperty("lever") BigDecimal leverage,
            @JsonProperty("clOrdId") String clientOid,
            @JsonProperty("sz") BigDecimal size,
            @JsonProperty("accFillSz") BigDecimal filledQty,
            @JsonProperty("px") BigDecimal price,
            @JsonProperty("fee") BigDecimal fee,
//            @JsonProperty("contract_val") BigDecimal contractVal, //TODO
            @JsonProperty("avgPx") BigDecimal priceAvg,
            @JsonProperty("side") String side,
//            @JsonProperty("order_type") Integer orderType,
            @JsonProperty("instId") String instrumentId,
            @JsonProperty("ordId") String orderId,
            @JsonProperty("uTime") Date timestamp,
            @JsonProperty("state") String state
    ) {
        this.leverage = leverage;
        this.clientOid = clientOid;
        this.size = size;
        this.filledQty = filledQty;
        this.price = price;
        this.fee = fee;
//        this.contractVal = contractVal;
        this.priceAvg = priceAvg;
        this.side = side;
//        this.orderType = orderType;
        this.instrumentId = instrumentId;
        this.orderId = orderId;
        this.timestamp = timestamp;
        this.state = state;
    }


    public BigDecimal getLeverage() {
        return leverage;
    }

    public String getClientOid() {
        return clientOid;
    }

    public BigDecimal getSize() {
        return size;
    }

    public BigDecimal getFilledQty() {
        return filledQty;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public BigDecimal getFee() {
        return fee;
    }

    public BigDecimal getPriceAvg() {
        return priceAvg;
    }

    public String getSide() {
        return side;
    }

    public String getInstrumentId() {
        return instrumentId;
    }

    public String getOrderId() {
        return orderId;
    }

    public Date getTimestamp() {
        return timestamp;
    }

    public String getState() {
        return state;
    }

    @Override
    public String toString() {
        return "OkExUserOrder{" +
                "leverage=" + leverage +
                ", clientOid='" + clientOid + '\'' +
                ", size=" + size +
                ", filledQty=" + filledQty +
                ", price=" + price +
                ", fee=" + fee +
//                ", contractVal=" + contractVal +
                ", priceAvg=" + priceAvg +
                ", type='" + side + '\'' +
//                ", orderType=" + orderType +
                ", instrumentId='" + instrumentId + '\'' +
                ", orderId='" + orderId + '\'' +
                ", timestamp=" + timestamp +
                ", state=" + state +
                '}';
    }
}
