package com.crypto.business;

import org.knowm.xchange.dto.Order;

import java.math.BigDecimal;

/**
 * Created by Sergey Shurmin on 4/16/17.
 */
public interface BusinessService {

    String placeMarketOrder(Order.OrderType orderType, BigDecimal amount);
}
