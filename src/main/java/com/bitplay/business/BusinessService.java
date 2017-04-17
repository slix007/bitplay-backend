package com.bitplay.business;

import org.knowm.xchange.dto.trade.UserTrades;

/**
 * Created by Sergey Shurmin on 4/16/17.
 */
public interface BusinessService {

    UserTrades fetchMyTradeHistory();
}
