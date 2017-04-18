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

}
