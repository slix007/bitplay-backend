package com.bitplay.business;

import com.bitplay.utils.Utils;

import org.knowm.xchange.dto.Order;
import org.knowm.xchange.dto.marketdata.OrderBook;
import org.knowm.xchange.dto.trade.UserTrades;

import java.math.BigDecimal;

/**
 * Created by Sergey Shurmin on 4/16/17.
 */
public interface BusinessService {

    UserTrades fetchMyTradeHistory();

    OrderBook getOrderBook();

    default BigDecimal getBestPrice(Order.OrderType orderType) {
        BigDecimal thePrice = null;
        if (orderType == Order.OrderType.BID) {
            thePrice = Utils.getBestAsks(getOrderBook().getAsks(), 1)
                    .get(0)
                    .getLimitPrice();
        } else if (orderType == Order.OrderType.ASK) {
            thePrice = Utils.getBestBids(getOrderBook().getBids(), 1)
                    .get(0)
                    .getLimitPrice();
        }
        return thePrice;
    }
}
