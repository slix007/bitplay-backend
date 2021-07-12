package com.bitplay.xchangestream.bitmex.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.List;

/**
 * Created by Sergey Shurmin on 9/26/17.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class BitmexOrderBook {

    private final String action; //partial, delete, update, insert
    private final List<BitmexOrder> bitmexOrderList;
    private final long gettingTimeEpochMs;

    public BitmexOrderBook(@JsonProperty("action") String action,
                           @JsonProperty("data") List<BitmexOrder> bitmexOrderList) {
        this.action = action;
        this.bitmexOrderList = bitmexOrderList;
        this.gettingTimeEpochMs = Instant.now().toEpochMilli();
    }

    public String getAction() {
        return action;
    }

    public List<BitmexOrder> getBitmexOrderList() {
        return bitmexOrderList;
    }

    public long getGettingTimeEpochMs() {
        return gettingTimeEpochMs;
    }

    @Override
    public String toString() {
        return "BitmexOrderBook{" +
                "action='" + action + '\'' +
                ", bitmexOrderList=" + bitmexOrderList +
                ", gettingTimeEpochMs=" + gettingTimeEpochMs +
                '}';
    }
}
