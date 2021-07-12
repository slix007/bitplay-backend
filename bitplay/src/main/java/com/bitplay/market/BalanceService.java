package com.bitplay.market;

import com.bitplay.market.model.FullBalance;
import com.bitplay.persistance.domain.settings.ContractType;
import com.bitplay.model.AccountBalance;
import com.bitplay.model.Pos;
import com.bitplay.xchange.dto.marketdata.OrderBook;

/**
 * Created by Sergey Shurmin on 11/14/17.
 */
public interface BalanceService {

    FullBalance recalcAndGetAccountInfo(AccountBalance account, Pos pObj, OrderBook orderBook, ContractType contractType,
            Pos positionXBTUSD, OrderBook orderBookXBTUSD);
}
