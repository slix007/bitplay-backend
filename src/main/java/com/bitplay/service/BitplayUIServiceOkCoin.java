package com.bitplay.service;

import com.bitplay.business.okcoin.OkCoinService;
import com.bitplay.model.AccountInfoJson;
import com.bitplay.model.OrderBookJson;
import com.bitplay.model.TradeRequest;
import com.bitplay.model.TradeResponseJson;
import com.bitplay.model.VisualTrade;

import org.knowm.xchange.dto.Order;
import org.knowm.xchange.dto.trade.UserTrades;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

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
        final UserTrades trades = service.fetchMyTradeHistory();

        List<VisualTrade> askTrades = trades.getTrades().stream()
                .sorted((o1, o2) -> o1.getTimestamp().before(o2.getTimestamp()) ? 1 : -1)
                .map(this::toVisualTrade)
                .collect(Collectors.toList());
        return askTrades;
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

    public TradeResponseJson doTrade(TradeRequest tradeRequest) {
        final BigDecimal amount = new BigDecimal(tradeRequest.getAmount());
        Order.OrderType orderType;
        switch (tradeRequest.getType()) {
            case BUY:
                orderType = Order.OrderType.BID;
                break;
            case SELL:
                orderType = Order.OrderType.ASK;
                break;
            default:
                throw new IllegalArgumentException("No such order type " + tradeRequest.getType());
        }
        final String orderId = service.placeMarketOrder(orderType, amount);
        return new TradeResponseJson(orderId, null);
    }

}
