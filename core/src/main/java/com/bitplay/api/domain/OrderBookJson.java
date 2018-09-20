package com.bitplay.api.domain;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Created by Sergey Shurmin on 4/4/17.
 */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class OrderBookJson {

    private List<OrderJson> bid = new ArrayList<>();
    private List<OrderJson> ask = new ArrayList<>();
    private BigDecimal lastPrice;
    private String ethBtcBal;
    private String bxbtBal;

    public static OrderBookJson empty() {
        return new OrderBookJson(new ArrayList<>(), new ArrayList<>(), BigDecimal.ZERO, "", "");
    }
}
