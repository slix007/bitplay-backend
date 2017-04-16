package com.bitplay.model;

/**
 * Created by Sergey Shurmin on 3/25/17.
 */
public class VisualTrade {

    String currency;
    String price;
    String amount;
    String orderType;
    String timestamp;

    public VisualTrade(String currency, String price, String amount, String orderType, String timestamp) {
        this.currency = currency;
        this.price = price;
        this.amount = amount;
        this.orderType = orderType;
        this.timestamp = timestamp;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public void setPrice(String price) {
        this.price = price;
    }

    public void setAmount(String amount) {
        this.amount = amount;
    }

    public void setOrderType(String orderType) {
        this.orderType = orderType;
    }

    public void setTimestamp(String timestamp) {
        this.timestamp = timestamp;
    }

    public String getCurrency() {
        return currency;
    }

    public String getPrice() {
        return price;
    }

    public String getAmount() {
        return amount;
    }

    public String getOrderType() {
        return orderType;
    }

    public String getTimestamp() {
        return timestamp;
    }

    @Override
    public String toString() {
        return "VisualTrade{" +
                "currency='" + currency + '\'' +
                ", price='" + price + '\'' +
                ", amount='" + amount + '\'' +
                ", orderType='" + orderType + '\'' +
                ", timestamp='" + timestamp + '\'' +
                '}';
    }
}