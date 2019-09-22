package com.bitplay.api.dto.ob;

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

    private BigDecimal lastPrice = BigDecimal.ZERO;

    private FutureIndexJson futureIndex = FutureIndexJson.empty();

}
