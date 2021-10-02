package com.bitplay.okex.v5.dto.adapter;

import com.bitplay.okex.v5.dto.result.Account;
import com.bitplay.okex.v5.dto.result.Account.AccountData;
import com.bitplay.okex.v5.dto.result.Account.AccountData.DetailsData;
import com.bitplay.xchange.dto.account.AccountInfoContracts;
import java.math.BigDecimal;
import java.math.RoundingMode;

public class AccountConverter {

    public static AccountInfoContracts convert(Account dto) {
        final boolean hasData = dto.getData() != null && !dto.getData().isEmpty();
        if (hasData) {
            final AccountData accountData = dto.getData().get(0);
            final boolean hasDetails = accountData.getDetails() != null;
            if (hasDetails) {
                final DetailsData acc = accountData.getDetails();
                BigDecimal equity = acc.getEq() == null ? null :
                        acc.getEq().setScale(8, 4);
                BigDecimal margin = acc.getFrozenBal() == null ? null :
                        acc.getFrozenBal().setScale(8, RoundingMode.HALF_UP);
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

            } else if (accountData.getTotalEq() != null) { // has total balance - if we use tool first time. No trades were made.
                BigDecimal equity = BigDecimal.ZERO;
//                        accountData.getTotalEq() == null ? null :
//                        accountData.getTotalEq().setScale(8, RoundingMode.HALF_UP);
                BigDecimal wallet = equity;
                BigDecimal available = equity;
                return new AccountInfoContracts(wallet, available, null, equity, null, null, null, null, null, null);
            }
        }

        return null;
    }

}
