package com.bitplay.domain;

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


}
