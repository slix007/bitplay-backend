package com.bitplay.api.domain;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

/**
 * Created by Sergey Shurmin on 4/4/17.
 */
@Getter
@Setter
public class OrderBookJson {

    private List<OrderJson> bid = new ArrayList<>();
    private List<OrderJson> ask = new ArrayList<>();
    private BigDecimal lastPrice;

}
