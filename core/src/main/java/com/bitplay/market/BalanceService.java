package com.bitplay.market;

import com.bitplay.market.dto.FullBalance;

import org.knowm.xchange.dto.account.AccountInfoContracts;
import org.knowm.xchange.dto.account.Position;
import org.knowm.xchange.dto.marketdata.OrderBook;

/**
 * Created by Sergey Shurmin on 11/14/17.
 */
public interface BalanceService {
    FullBalance recalcAndGetAccountInfo(AccountInfoContracts accountInfoContracts, Position pObj, OrderBook orderBook);
}
