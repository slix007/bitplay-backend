package com.bitplay.persistance.domain.settings.contracttype;

import com.bitplay.persistance.domain.settings.BitmexContractType;
import com.bitplay.persistance.domain.settings.ContractType;
import org.knowm.xchange.currency.CurrencyPair;

import java.math.BigDecimal;

public class BitmexCTThisWeek extends BitmexContractTypeEx {

    private String firstCurrency; //"XBT" "ETH"
    private String secondCurrency; // "M20", "Z19", ...

    public static BitmexContractTypeEx parse(CurrencyPair currencyPair) {
        BitmexContractTypeEx resultType = null;
        if (currencyPair != null) {
            String first = currencyPair.base.getCurrencyCode();
            String second = currencyPair.counter.getCurrencyCode();
            for (BitmexContractType type : BitmexContractType.values()) {
                CurrencyPair pair = type.getCurrencyPair();
                if (first.equals(pair.base.getCurrencyCode()) && second.equals(pair.counter.getCurrencyCode())) {
                    resultType = type;
                    break;
                }
            }
        }
        return resultType;
    }

    @Override
    public CurrencyPair getCurrencyPair() {
        return null;
    }

    @Override
    public String getName() {
        return null;
    }
}
