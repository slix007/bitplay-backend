package com.bitplay.market.bitmex;

import com.bitplay.arbitrage.BestQuotes;
import com.bitplay.market.MarketService;
import com.bitplay.market.model.MoveResponse;
import com.bitplay.market.model.TradeResponse;

import org.knowm.xchange.currency.Currency;
import org.knowm.xchange.dto.Order;
import org.knowm.xchange.dto.account.AccountInfo;
import org.knowm.xchange.dto.account.Balance;
import org.knowm.xchange.dto.marketdata.OrderBook;
import org.knowm.xchange.dto.trade.LimitOrder;
import org.knowm.xchange.dto.trade.UserTrades;
import org.knowm.xchange.service.trade.TradeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

import io.reactivex.Observable;
import io.swagger.client.ApiException;
import io.swagger.client.api.APIKeyApi;
import io.swagger.client.api.UserApi;
import io.swagger.client.model.APIKey;
import io.swagger.client.model.Wallet;

/**
 * Created by Sergey Shurmin on 4/29/17.
 */
@Service("bitmex")
public class BitmexService extends MarketService {
    private final static Logger logger = LoggerFactory.getLogger(BitmexService.class);


    @Override
    public void initializeMarket(String key, String secret) {
        APIKeyApi apiInstance = new APIKeyApi();
        String apiKeyID = key; // String | API Key ID (public component).
        try {
            final List<APIKey> apiKeys = apiInstance.aPIKeyGet(false);
//            logger.info(apiKeys);
        } catch (ApiException e) {
            System.err.println("Exception when calling APIKeyApi#aPIKeyDisable");
            e.printStackTrace();
        }

    }

    @Override
    public UserTrades fetchMyTradeHistory() {
        return null;
    }

    @Override
    public OrderBook getOrderBook() {
        return orderBook;
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

    @Override
    public Observable<OrderBook> observeOrderBook() {
        return null;
    }

    @Override
    public AccountInfo getAccountInfo() {
        AccountInfo accountInfo = new AccountInfo();
        final UserApi userApi = new UserApi();
        try {
            final Wallet btc = userApi.userGetWallet("XBT");
            final BigDecimal btcAmount = btc.getAmount();
            final Wallet usd = userApi.userGetWallet("USD");
            final BigDecimal usdAmount = usd.getAmount();

            accountInfo = new AccountInfo(new org.knowm.xchange.dto.account.Wallet(
                    new Balance(Currency.XBT, btcAmount),
                    new Balance(Currency.USD, usdAmount)
            ));
        } catch (ApiException e) {
            logger.error("Get account info error");
        }
        return accountInfo;
    }
}
