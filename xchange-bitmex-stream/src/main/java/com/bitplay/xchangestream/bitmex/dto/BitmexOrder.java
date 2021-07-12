package com.bitplay.xchangestream.bitmex.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;

@JsonIgnoreProperties(ignoreUnknown = true)
public class BitmexOrder {

    private final String symbol; //XBTUSD
    private final Long id;
    private final Side side;//Sell,Buy
    private final BigDecimal size;
    private final BigDecimal price;

    public BitmexOrder(@JsonProperty("symbol") String symbol,
                       @JsonProperty("id") Long id,
                       @JsonProperty("side") Side side,
                       @JsonProperty("size") BigDecimal size,
                       @JsonProperty("price") BigDecimal price) {
        this.symbol = symbol;
        this.id = id;
        this.side = side;
        this.size = size;
        this.price = price;
    }

    public String getSymbol() {
        return symbol;
    }

    public Long getId() {
        return id;
    }

    public Side getSide() {
        return side;
    }

    public BigDecimal getSize() {
        return size;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public enum Side {
        Sell, Buy
    }

    @Override
    public String toString() {
        return "BitmexOrder{" +
                "symbol='" + symbol + '\'' +
                ", id=" + id +
                ", side=" + side +
                ", size=" + size +
                ", price=" + price +
                '}';
    }
}
