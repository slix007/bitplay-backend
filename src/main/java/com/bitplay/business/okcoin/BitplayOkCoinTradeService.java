package com.bitplay.business.okcoin;

import org.knowm.xchange.Exchange;
import org.knowm.xchange.dto.Order;
import org.knowm.xchange.dto.trade.MarketOrder;
import org.knowm.xchange.okcoin.OkCoinAdapters;
import org.knowm.xchange.okcoin.service.OkCoinTradeService;

import java.io.IOException;

/**
 * Created by Sergey Shurmin on 4/17/17.
 */
public class BitplayOkCoinTradeService {

    public static String placeMarketOrder(MarketOrder marketOrder, OkCoinTradeService okCoinTradeService) throws IOException {
//        return super.placeMarketOrder(marketOrder);
        String marketOrderType = null;
        String rate = null;
        String amount = null;

        if (marketOrder.getType().equals(Order.OrderType.BID)) {
            marketOrderType = "buy_market";
            rate = //Total Amount you want to buy.
            amount = marketOrder.getTradableAmount().toPlainString();
        } else {
            marketOrderType = "sell_market";
            rate = "1";
            amount = marketOrder.getTradableAmount().toPlainString();
        }

        long orderId = okCoinTradeService.trade(OkCoinAdapters.adaptSymbol(marketOrder.getCurrencyPair()), marketOrderType, rate, amount).getOrderId();
        return String.valueOf(orderId);

    }
}
