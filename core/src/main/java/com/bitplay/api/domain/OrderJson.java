package com.bitplay.api.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Created by Sergey Shurmin on 4/22/17.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class OrderJson {
    String id;
    String status;
    String currency;
    String price;
    String amount;
    String orderType;
    String timestamp;
    String amountInBtc;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public String getPrice() {
        return price;
    }

    public void setPrice(String price) {
        this.price = price;
    }

    public String getAmount() {
        return amount;
    }

    public void setAmount(String amount) {
        this.amount = amount;
    }

    public String getOrderType() {
        return orderType;
    }

    public void setOrderType(String orderType) {
        this.orderType = orderType;
    }

    public String getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(String timestamp) {
        this.timestamp = timestamp;
    }

    public String getAmountInBtc() {
        return amountInBtc;
    }

    public void setAmountInBtc(String amountInBtc) {
        this.amountInBtc = amountInBtc;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        OrderJson orderJson = (OrderJson) o;

        if (currency != null ? !currency.equals(orderJson.currency) : orderJson.currency != null) return false;
        if (price != null ? !price.equals(orderJson.price) : orderJson.price != null) return false;
        if (amount != null ? !amount.equals(orderJson.amount) : orderJson.amount != null) return false;
        return orderType != null ? orderType.equals(orderJson.orderType) : orderJson.orderType == null;
    }

    @Override
    public int hashCode() {
        int result = currency != null ? currency.hashCode() : 0;
        result = 31 * result + (price != null ? price.hashCode() : 0);
        result = 31 * result + (amount != null ? amount.hashCode() : 0);
        result = 31 * result + (orderType != null ? orderType.hashCode() : 0);
        return result;
    }
}
