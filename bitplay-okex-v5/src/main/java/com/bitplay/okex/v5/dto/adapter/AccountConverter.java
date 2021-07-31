package com.bitplay.okex.v5.dto.adapter;

import com.bitplay.okex.v5.dto.result.Account;
import com.bitplay.okex.v5.dto.result.Account.AccountData.DetailsData;
import com.bitplay.okex.v5.dto.result.Accounts;
import com.bitplay.xchange.currency.Currency;
import com.bitplay.xchange.dto.account.AccountInfoContracts;
import java.math.BigDecimal;

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

    public static AccountInfoContracts convert(Account dto) {
        if (dto.getData() != null && !dto.getData().isEmpty() && dto.getData().get(0).getDetails() != null) {
            final DetailsData acc = dto.getData().get(0).getDetails();
            BigDecimal equity = acc.getEq() == null ? null :
                    acc.getEq().setScale(8, 4);
            BigDecimal margin = acc.getIsoEq() == null ? null :
                    acc.getIsoEq().setScale(8, 4);
            BigDecimal upl = acc.getUpl() == null ? null :
                    acc.getUpl().setScale(8, 4);
            BigDecimal wallet = equity == null || upl == null ? null :
                    equity.subtract(upl).setScale(8, 4);
            BigDecimal available = acc.getAvailEq() == null ? null :
                    acc.getAvailEq().setScale(8, 4);
//        BigDecimal available = equity.subtract(margin).setScale(8, 4);
            // TODO
            BigDecimal rpl = acc.getLiab() == null ? BigDecimal.ZERO :
                    acc.getLiab().setScale(8, 4); //
            BigDecimal riskRate = acc.getMgnRatio() == null ? BigDecimal.ZERO :
                    acc.getMgnRatio().setScale(8, 4);
            return new AccountInfoContracts(wallet, available, (BigDecimal) null, equity, (BigDecimal) null, (BigDecimal) null, margin, upl, rpl, riskRate);
        }

        return null;
    }

}
