package com.bitplay.okex.v3.service.futures.adapter;

import com.bitplay.okex.v3.dto.futures.result.Account;
import com.bitplay.okex.v3.dto.futures.result.Accounts;
import java.math.BigDecimal;
import org.knowm.xchange.currency.Currency;
import org.knowm.xchange.dto.account.AccountInfoContracts;

public class AccountConverter {

    public static AccountInfoContracts convert(Accounts accounts, Currency baseTool) {
        Account acc;
        switch (baseTool.getCurrencyCode()) {
            case "BTC":
                acc = accounts.getInfo().getBtc();
                break;
            case "ETH":
                acc = accounts.getInfo().getEth();
                break;
            default:
                throw new IllegalArgumentException("Unsuported baseTool " + baseTool);
        }
        return convert(acc);
    }

    public static AccountInfoContracts convert(Account acc) {
        BigDecimal equity = acc.getEquity().setScale(8, 4);
        BigDecimal margin = acc.getMargin().setScale(8, 4);
        BigDecimal upl = acc.getUnrealized_pnl().setScale(8, 4);
        BigDecimal wallet = equity.subtract(upl).setScale(8, 4);
        BigDecimal available = acc.getTotal_avail_balance().setScale(8, 4);
//        BigDecimal available = equity.subtract(margin).setScale(8, 4);
        BigDecimal rpl = acc.getRealized_pnl().setScale(8, 4);
        BigDecimal riskRate = acc.getMargin_ratio().setScale(8, 4);
        return new AccountInfoContracts(wallet, available, (BigDecimal) null, equity, (BigDecimal) null, (BigDecimal) null, margin, upl, rpl, riskRate);
    }

}
