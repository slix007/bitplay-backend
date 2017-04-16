package com.crypto.service;

import com.crypto.model.AccountInfoJson;
import com.crypto.model.OrderBookJson;
import com.crypto.model.VisualTrade;
import com.crypto.business.okcoin.OkCoinService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Created by Sergey Shurmin on 4/4/17.
 */
@Component("OkCoin")
public class BitplayUIServiceOkCoin extends AbstractBitplayUIService<OkCoinService> {

    private static final Logger logger = LoggerFactory.getLogger(BitplayUIServiceOkCoin.class);

    @Autowired
    OkCoinService service;

    @Override
    public OkCoinService getBusinessService() {
        return service;
    }

    public OrderBookJson getOrderBook() {
        return convertOrderBookAndFilter(service.getOrderBook());
    }

    @Override
    public List<VisualTrade> fetchTrades() {
        return null;
    }

    @Override
    public OrderBookJson fetchOrderBook() {
//        final OrderBook orderBook = service.fetchOrderBook();
//        final OrderBookJson bestOrderBookJson = getBestOrderBookJson(orderBook);
//        return bestOrderBookJson;
        return null;
    }

    @Override
    public AccountInfoJson getAccountInfo() {
        return convertAccountInfo(service.fetchAccountInfo());
    }
}
