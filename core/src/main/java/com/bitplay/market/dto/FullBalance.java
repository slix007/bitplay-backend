package com.bitplay.market.dto;

import org.knowm.xchange.dto.account.AccountInfoContracts;
import org.knowm.xchange.dto.account.Position;
import org.knowm.xchange.dto.marketdata.OrderBook;

/**
 * Created by Sergey Shurmin on 11/12/17.
 */
public class FullBalance {

    AccountInfoContracts accountInfoContracts;
    Position position;
    OrderBook orderBook;

    public AccountInfoContracts getAccountInfoContracts() {
        return accountInfoContracts;
    }

    public void setAccountInfoContracts(AccountInfoContracts accountInfoContracts) {
        this.accountInfoContracts = accountInfoContracts;
    }

    public Position getPosition() {
        return position;
    }

    public void setPosition(Position position) {
        this.position = position;
    }

    public OrderBook getOrderBook() {
        return orderBook;
    }

    public void setOrderBook(OrderBook orderBook) {
        this.orderBook = orderBook;
    }
}
