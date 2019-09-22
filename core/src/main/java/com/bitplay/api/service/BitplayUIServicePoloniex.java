package com.bitplay.api.service;

import com.bitplay.api.dto.AccountInfoJson;
import com.bitplay.api.dto.TickerJson;
import com.bitplay.api.dto.TradeRequestJson;
import com.bitplay.api.dto.TradeResponseJson;
import com.bitplay.api.dto.ob.FutureIndexJson;
import com.bitplay.arbitrage.dto.SignalType;
import com.bitplay.market.model.PlaceOrderArgs;
import com.bitplay.market.model.TradeResponse;
import com.bitplay.market.polonex.PoloniexService;
import java.math.BigDecimal;
import org.knowm.xchange.currency.Currency;
import org.knowm.xchange.dto.Order;
import org.knowm.xchange.dto.account.AccountInfo;
import org.knowm.xchange.dto.account.Position;
import org.knowm.xchange.dto.account.Wallet;
import org.knowm.xchange.poloniex.dto.trade.PoloniexTradeResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Created by Sergey Shurmin on 3/25/17.
 */
@Component("Poloniex")
public class BitplayUIServicePoloniex extends AbstractBitplayUIService<PoloniexService> {

    @Autowired
    PoloniexService poloniexService;

    @Override
    public PoloniexService getBusinessService() {
        return poloniexService;
    }

    @Override
    protected AccountInfoJson convertAccountInfo(AccountInfo accountInfo, Position position) {
        if (accountInfo == null) {
            return new AccountInfoJson(null, null, null);
        }
        final Wallet wallet = accountInfo.getWallet();
        return new AccountInfoJson(wallet.getBalance(Currency.BTC).getAvailable().toPlainString(),
                wallet.getBalance(PoloniexService.CURRENCY_USDT).getAvailable().toPlainString(),
                accountInfo.toString());
    }

    public TickerJson getTicker() {
        return convertTicker(poloniexService.getTicker());
    }

    @Override
    public FutureIndexJson getFutureIndex() {
        return null;
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
        TradeResponse tradeResponse = null;
        if (tradeRequestJson.getPlacementType() == TradeRequestJson.PlacementType.TAKER) {
            tradeResponse = poloniexService.placeTakerOrder(orderType, amount);
        } else if (tradeRequestJson.getPlacementType() == TradeRequestJson.PlacementType.MAKER) {
            SignalType signalType;
            if (orderType.equals(Order.OrderType.ASK)) {
                signalType = SignalType.MANUAL_SELL;
            } else if (orderType.equals(Order.OrderType.BID)) {
                signalType = SignalType.MANUAL_BUY;
            } else {
                return new TradeResponseJson("Wrong orderType", "Wrong orderType");
            }
            Long tradeId = poloniexService.getArbitrageService().getLastInProgressTradeId();
            tradeResponse = poloniexService.placeOrder(new PlaceOrderArgs(orderType, amount, null,
                    null, signalType, 1, tradeId, signalType.getCounterName(), null));
        }

        final PoloniexTradeResponse poloniexTradeResponse = tradeResponse.getSpecificResponse() != null
                ? (PoloniexTradeResponse) tradeResponse.getSpecificResponse() : null;
        return new TradeResponseJson(tradeResponse.getOrderId(),
                poloniexTradeResponse);
    }
}
