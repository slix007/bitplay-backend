package org.knowm.xchange.bitmex;

import java.math.BigDecimal;
import java.util.List;

import io.swagger.client.ApiException;
import io.swagger.client.api.OrderBookApi;
import io.swagger.client.model.OrderBookL2;

/**
 * Created by Sergey Shurmin on 5/3/17.
 */
public class BitmexPublic extends Bitmex {


    public BitmexPublic() {
        super();
    }


    public List<OrderBookL2> getOrderBook(String symbol, Integer depth) throws ApiException {
        final OrderBookApi orderBookApi = new OrderBookApi(apiClient);
        return orderBookApi.orderBookGetL2(symbol, new BigDecimal(depth));
    }
}
