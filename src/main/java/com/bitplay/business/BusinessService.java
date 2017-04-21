package com.bitplay.business;

import com.bitplay.business.model.TradeResponse;
import com.bitplay.utils.Utils;

import org.knowm.xchange.dto.Order;
import org.knowm.xchange.dto.marketdata.OrderBook;
import org.knowm.xchange.dto.trade.LimitOrder;
import org.knowm.xchange.dto.trade.UserTrades;

import java.math.BigDecimal;

/**
 * Created by Sergey Shurmin on 4/16/17.
 */
public interface BusinessService {

    BigDecimal MAKER_QUOTE_DELTA = new BigDecimal("0.1");

    UserTrades fetchMyTradeHistory();

    OrderBook getOrderBook();

    TradeResponse placeMakerOrder(Order.OrderType orderType, BigDecimal amount);

    default BigDecimal getTotalPriceOfAmountToBuy(BigDecimal requiredAmountToBuy) {
        BigDecimal totalPrice = BigDecimal.ZERO;
        int index = 0;
        final LimitOrder limitOrder1 = Utils.getBestAsks(getOrderBook().getAsks(), 1).get(index);
        BigDecimal totalAmountToBuy = limitOrder1.getTradableAmount().compareTo(requiredAmountToBuy) == -1
                ? limitOrder1.getTradableAmount()
                : requiredAmountToBuy;

        totalPrice = totalPrice.add(totalAmountToBuy.multiply(limitOrder1.getLimitPrice()));

        while (totalAmountToBuy.compareTo(requiredAmountToBuy) == -1) {
            index++;
            final LimitOrder lo = Utils.getBestAsks(getOrderBook().getAsks(), index).get(index);
            final BigDecimal toBuyLeft = requiredAmountToBuy.subtract(totalAmountToBuy);
            BigDecimal amountToBuyForItem = lo.getTradableAmount().compareTo(toBuyLeft) == -1
                    ? lo.getTradableAmount()
                    : toBuyLeft;
            totalPrice = totalPrice.add(amountToBuyForItem.multiply(lo.getLimitPrice()));
            totalAmountToBuy = totalAmountToBuy.add(amountToBuyForItem);
        }

        return totalPrice;
    }
}
