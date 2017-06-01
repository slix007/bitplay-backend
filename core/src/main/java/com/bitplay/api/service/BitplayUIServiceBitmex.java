package com.bitplay.api.service;

import com.bitplay.api.domain.AccountInfoJson;
import com.bitplay.api.domain.TradeRequestJson;
import com.bitplay.api.domain.TradeResponseJson;
import com.bitplay.api.domain.VisualTrade;
import com.bitplay.market.bitmex.BitmexService;
import com.bitplay.market.model.TradeResponse;

import org.knowm.xchange.bitmex.BitmexAdapters;
import org.knowm.xchange.dto.Order;
import org.knowm.xchange.dto.account.AccountInfo;
import org.knowm.xchange.dto.account.Balance;
import org.knowm.xchange.dto.account.Wallet;
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
@Component("Bitmex")
public class BitplayUIServiceBitmex extends AbstractBitplayUIService<BitmexService> {

    private static final Logger logger = LoggerFactory.getLogger(BitplayUIServiceBitmex.class);

    @Autowired
    BitmexService service;

    @Override
    public BitmexService getBusinessService() {
        return service;
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
//            orderId = service.placeTakerOrder(orderType, amount);
        } else if (tradeRequestJson.getPlacementType() == TradeRequestJson.PlacementType.MAKER) {
            final TradeResponse tradeResponse = service.placeMakerOrder(orderType, amount, null, true);
            orderId = tradeResponse.getOrderId();
        }

        return new TradeResponseJson(orderId, null);
    }

    @Override
    protected AccountInfoJson convertAccountInfo(AccountInfo accountInfo) {
        if (accountInfo == null) {
            return new AccountInfoJson(null, null, null);
        }
        final Wallet wallet = accountInfo.getWallet();
        final Balance walletBalance = wallet.getBalance(BitmexAdapters.WALLET_CURRENCY);
        final Balance marginBalance = wallet.getBalance(BitmexAdapters.MARGIN_CURRENCY);
        final Balance position = wallet.getBalance(BitmexAdapters.POSITION_CURRENCY);

        return new AccountInfoJson(
                walletBalance.getTotal().toPlainString(),
                walletBalance.getAvailable().toPlainString(),
                marginBalance.getTotal().toPlainString(),
                position != null ? position.getAvailable().toPlainString() : "0",
                accountInfo.toString());
    }

}
