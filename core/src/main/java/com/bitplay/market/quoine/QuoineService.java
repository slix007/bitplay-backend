package com.bitplay.market.quoine;

import com.bitplay.arbitrage.ArbitrageService;
import com.bitplay.arbitrage.BestQuotes;
import com.bitplay.arbitrage.SignalType;
import com.bitplay.market.MarketService;
import com.bitplay.market.model.MoveResponse;
import com.bitplay.market.model.TradeResponse;

import org.knowm.xchange.Exchange;
import org.knowm.xchange.dto.Order;
import org.knowm.xchange.dto.account.AccountInfo;
import org.knowm.xchange.dto.marketdata.OrderBook;
import org.knowm.xchange.dto.trade.LimitOrder;
import org.knowm.xchange.dto.trade.UserTrades;
import org.knowm.xchange.service.trade.TradeService;
import org.slf4j.Logger;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

import io.reactivex.Observable;

/**
 * Created by Sergey Shurmin on 4/29/17.
 */
@Service("quoine")
public class QuoineService extends MarketService {

    private final static String NAME = "quoine";

    @Override
    public ArbitrageService getArbitrageService() {
        return null;
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public Logger getTradeLogger() {
        return null;
    }

    @Override
    protected Exchange getExchange() {
        return null;
    }

    @Override
    public void initializeMarket(String key, String secret) {

    }

    @Override
    public boolean isAffordable(Order.OrderType orderType, BigDecimal tradableAmount) {
        return false;
    }

    @Override
    public UserTrades fetchMyTradeHistory() {
        return null;
    }

    @Override
    public OrderBook getOrderBook() {
        return null;
    }

    @Override
    public TradeResponse placeMakerOrder(Order.OrderType orderType, BigDecimal amountInContracts, BestQuotes bestQuotes, SignalType signalType) {
        return null;
    }

    @Override
    public TradeService getTradeService() {
        return null;
    }

    @Override
    public MoveResponse moveMakerOrder(LimitOrder limitOrder, SignalType signalType) {
        return null;
    }

    @Override
    protected BigDecimal getMakerPriceStep() {
        return null;
    }

    @Override
    protected BigDecimal getMakerDelta() {
        return null;
    }

    @Override
    public Observable<OrderBook> getOrderBookObservable() {
        return null;
    }

    @Override
    public AccountInfo getAccountInfo() {
        return null;
    }

    @Override
    public String getPosition() {
        return null;
    }

}
