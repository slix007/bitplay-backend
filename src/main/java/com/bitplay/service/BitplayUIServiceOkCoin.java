package com.bitplay.service;

import com.bitplay.market.model.TradeResponse;
import com.bitplay.market.okcoin.OkCoinService;
import com.bitplay.domain.AccountInfoJson;
import com.bitplay.domain.OrderBookJson;
import com.bitplay.domain.TradeRequestJson;
import com.bitplay.domain.TradeResponseJson;
import com.bitplay.domain.VisualTrade;

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
        return convertAccountInfo(service.getAccountInfo());
    }

    public TradeResponseJson doTrade(TradeRequestJson tradeRequestJson) {
        final BigDecimal amount = new BigDecimal(tradeRequestJson.getAmount());
        Order.OrderType orderType;
        switch (tradeRequestJson.getType()) {
            case BUY:
                orderType = Order.OrderType.BID;
                break;
            case SELL:
                orderType = Order.OrderType.ASK;
                break;
            default:
                throw new IllegalArgumentException("No such order type " + tradeRequestJson.getType());
        }

        String orderId = null;
        if (tradeRequestJson.getPlacementType() == TradeRequestJson.PlacementType.TAKER) {
            orderId = service.placeTakerOrder(orderType, amount);
        } else if (tradeRequestJson.getPlacementType() == TradeRequestJson.PlacementType.MAKER) {
            final TradeResponse tradeResponse = service.placeMakerOrder(orderType, amount, null);
            orderId = tradeResponse.getOrderId();
        }

        return new TradeResponseJson(orderId, null);
    }

}
