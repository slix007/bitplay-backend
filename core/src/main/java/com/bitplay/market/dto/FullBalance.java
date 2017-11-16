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
    String tempValues;

    public FullBalance(AccountInfoContracts accountInfoContracts, Position position, OrderBook orderBook, String tempValues) {
        this.accountInfoContracts = accountInfoContracts;
        this.position = position;
        this.orderBook = orderBook;
        this.tempValues = tempValues;
    }

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

    public String getTempValues() {
        return tempValues;
    }

    public void setTempValues(String tempValues) {
        this.tempValues = tempValues;
    }
}
