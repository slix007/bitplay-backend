package com.bitplay.market.quoine;

import com.bitplay.arbitrage.ArbitrageService;
import com.bitplay.arbitrage.posdiff.PosDiffService;
import com.bitplay.arbitrage.dto.BestQuotes;
import com.bitplay.arbitrage.dto.SignalType;
import com.bitplay.external.SlackNotifications;
import com.bitplay.market.BalanceService;
import com.bitplay.market.LogService;
import com.bitplay.market.MarketService;
import com.bitplay.market.MarketStaticData;
import com.bitplay.market.model.Affordable;
import com.bitplay.market.model.MoveResponse;
import com.bitplay.market.model.PlaceOrderArgs;
import com.bitplay.market.model.TradeResponse;
import com.bitplay.persistance.PersistenceService;
import com.bitplay.persistance.domain.fluent.FplayOrder;
import com.bitplay.persistance.domain.settings.ContractType;
import java.math.BigDecimal;
import org.knowm.xchange.Exchange;
import org.knowm.xchange.dto.Order;
import org.knowm.xchange.service.trade.TradeService;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;

/**
 * Created by Sergey Shurmin on 4/29/17.
 */
@Service("quoine")
@Scope(scopeName = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class QuoineService extends MarketService {

    private static final MarketStaticData MARKET_STATIC_DATA = MarketStaticData.OKEX;
    public static final String NAME = MARKET_STATIC_DATA.getName();

    @Override
    public PosDiffService getPosDiffService() {
        return null;
    }

    @Override
    public ArbitrageService getArbitrageService() {
        return null;
    }

    @Override
    public BalanceService getBalanceService() {
        return null;
    }

    @Override
    public boolean isMarketStopped() {
        return false;
    }

    @Override
    public MarketStaticData getMarketStaticData() {
        return MARKET_STATIC_DATA;
    }

    @Override
    public LogService getTradeLogger() {
        return null;
    }

    @Override
    public LogService getLogger() {
        return null;
    }

    @Override
    protected Exchange getExchange() {
        return null;
    }

    @Override
    public void initializeMarket(String key, String secret, ContractType contractType, Object... exArgs) {

    }

    @Override
    public SlackNotifications getSlackNotifications() {
        return null;
    }

    @Override
    protected boolean onReadyState() {
        // nothing for now
        return true;
    }

    @Override
    public ContractType getContractType() {
        return null;
    }

    @Override
    protected void iterateOpenOrdersMoveAsync(Object... iterateArgs) {

    }

    @Override
    protected void postOverload() {
    }

    @Override
    public boolean isAffordable(Order.OrderType orderType, BigDecimal tradableAmount) {
        return false;
    }

    @Override
    public Affordable recalcAffordable() {
        return null;
    }

    @Override
    public TradeResponse placeOrder(PlaceOrderArgs placeOrderArgs) {
        return null;
    }

    @Override
    public TradeResponse placeOrderOnSignal(Order.OrderType orderType, BigDecimal amountInContracts, BestQuotes bestQuotes, SignalType signalType) {
        return null;
    }

    @Override
    public TradeService getTradeService() {
        return null;
    }

    @Override
    public MoveResponse moveMakerOrder(FplayOrder fplayOrder, BigDecimal bestMarketPrice, Object... reqMovingArgs) {
        return null;
    }

    @Override
    public String getPositionAsString() {
        return null;
    }

    @Override
    public String fetchPosition() {
        return null;
    }

    @Override
    public PersistenceService getPersistenceService() {
        return null;
    }

    @Override
    public boolean checkLiquidationEdge(Order.OrderType orderType) {
        return false;
    }
}
