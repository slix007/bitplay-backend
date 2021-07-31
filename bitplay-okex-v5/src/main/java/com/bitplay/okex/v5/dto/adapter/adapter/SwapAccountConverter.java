package com.bitplay.okex.v5.dto.adapter.adapter;

import com.bitplay.okex.v5.dto.result.SwapAccount;
import com.bitplay.okex.v5.dto.result.SwapAccounts;
import com.bitplay.xchange.dto.account.AccountInfoContracts;
import java.math.BigDecimal;

public class SwapAccountConverter {

    //    public static AccountInfoContracts convert(Accounts accounts, Currency baseTool) {
//        Account acc;
//        switch (baseTool.getCurrencyCode()) {
//            case "BTC":
//                acc = accounts.getInfo().getBtc();
//                break;
//            case "ETH":
//                acc = accounts.getInfo().getEth();
//                break;
//            default:
//                throw new IllegalArgumentException("Unsuported baseTool " + baseTool);
//        }
//        return convert(acc);
//    }
//
    public static AccountInfoContracts convert(SwapAccounts swapAccounts) {
        final SwapAccount acc = swapAccounts.getInfo();
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