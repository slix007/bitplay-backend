package com.bitplay.market.bitmex;

import com.bitplay.market.MarketService;
import com.bitplay.market.arbitrage.BestQuotes;
import com.bitplay.market.model.MoveResponse;
import com.bitplay.market.model.TradeResponse;

import org.knowm.xchange.dto.Order;
import org.knowm.xchange.dto.marketdata.OrderBook;
import org.knowm.xchange.dto.trade.LimitOrder;
import org.knowm.xchange.dto.trade.UserTrades;
import org.knowm.xchange.service.trade.TradeService;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

/**
 * Created by Sergey Shurmin on 4/29/17.
 */
@Service
public class BitmexService extends MarketService {
    @Override
    public UserTrades fetchMyTradeHistory() {
        return null;
    }

    @Override
    public OrderBook getOrderBook() {
        return null;
    }

    @Override
    public TradeResponse placeMakerOrder(Order.OrderType orderType, BigDecimal amount, BestQuotes bestQuotes) {
        return null;
    }

    @Override
    public TradeService getTradeService() {
        return null;
    }

    @Override
    public MoveResponse moveMakerOrder(LimitOrder limitOrder) {
        return null;
    }

    @Override
    protected BigDecimal getMakerStep() {
        return null;
    }

    @Override
    protected BigDecimal getMakerDelta() {
        return null;
    }
}
