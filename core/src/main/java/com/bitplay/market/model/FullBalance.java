package com.bitplay.market.model;

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

    public Position getPosition() {
        return position;
    }

    public OrderBook getOrderBook() {
        return orderBook;
    }

    public String getTempValues() {
        return tempValues;
    }

    public boolean isValid() {
        // Corr/adj при equity = 0
        // 1. Изменить условие: если на любой бирже e_best = 0 && e_avg = 0 && e_mark != 0,
        // то corr, adj, stop all actions при mdc, stop all actions при signal limit не выполняются.
        if (accountInfoContracts == null
                || accountInfoContracts.geteBest() == null
                || accountInfoContracts.geteAvg() == null
                || accountInfoContracts.geteMark() == null) {
            return false;
        }
        // Типичная ликвидация: e_mark == e_best == e_avg == 0;
        // Типичный проскок Okex: e_best == e_avg == 0 && e_mark != 0.
        if (accountInfoContracts.geteBest().signum() == 0
                && accountInfoContracts.geteAvg().signum() == 0
                && accountInfoContracts.geteMark().signum() != 0) {
            return false;
        }
        return true;
    }
}
