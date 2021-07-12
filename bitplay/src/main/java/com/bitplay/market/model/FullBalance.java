package com.bitplay.market.model;

import com.bitplay.model.AccountBalance;
import com.bitplay.model.Pos;

/**
 * Created by Sergey Shurmin on 11/12/17.
 */
public class FullBalance {

    /**
     * UPL is based on orderBook<br> if contractType is ETH it includes both positions based on both orderBooks.
     */
    AccountBalance accountBalance;
    Pos pos;
    String tempValues;

    public FullBalance(AccountBalance accountBalance, Pos pos, String tempValues) {
        this.accountBalance = accountBalance;
        this.pos = pos;
        this.tempValues = tempValues;
    }

    public AccountBalance getAccountBalance() {
        return accountBalance;
    }

    public Pos getPos() {
        return pos;
    }

    public String getTempValues() {
        return tempValues;
    }

    public boolean isValid() {
        // Corr/adj при equity = 0
        // 1. Изменить условие: если на любой бирже e_best = 0 && e_avg = 0 && e_mark != 0,
        // то corr, adj, stop all actions при mdc, stop all actions при signal limit не выполняются.
        if (accountBalance == null
                || accountBalance.getEBest() == null
                || accountBalance.getEAvg() == null
                || accountBalance.getEMark() == null) {
            return false;
        }
        // Типичная ликвидация: e_mark == e_best == e_avg == 0;
        // Типичный проскок Okex: e_best == e_avg == 0 && e_mark != 0.
        if (accountBalance.getEBest().signum() == 0
                && accountBalance.getEAvg().signum() == 0
                && accountBalance.getEMark().signum() != 0) {
            return false;
        }
        return true;
    }
}
