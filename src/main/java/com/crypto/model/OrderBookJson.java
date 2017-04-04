package com.crypto.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by Sergey Shurmin on 4/4/17.
 */
public class OrderBookJson {

    List<OrderJson> bid = new ArrayList<>();
    List<OrderJson> ask = new ArrayList<>();

    public List<OrderJson> getBid() {
        return bid;
    }

    public void setBid(List<OrderJson> bid) {
        this.bid = bid;
    }

    public List<OrderJson> getAsk() {
        return ask;
    }

    public void setAsk(List<OrderJson> ask) {
        this.ask = ask;
    }

    public static class OrderJson {
        String currency;
        String price;
        String amount;
        String orderType;
        String timestamp;

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
    }
}
