package info.bitrich.xchangestream.bitmex.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Created by Sergey Shurmin on 9/26/17.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class BitmexOrderBook {

    private final String action; //partial, delete, update, insert
    private final List<BitmexOrder> bitmexOrderList;

    public BitmexOrderBook(@JsonProperty("action") String action,
                           @JsonProperty("data") List<BitmexOrder> bitmexOrderList) {
        this.action = action;
        this.bitmexOrderList = bitmexOrderList;
    }

    public String getAction() {
        return action;
    }

    public List<BitmexOrder> getBitmexOrderList() {
        return bitmexOrderList;
    }

}
