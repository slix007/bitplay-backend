package com.bitplay.persistance.domain.settings.contracttype;

import com.bitplay.market.bitmex.BitmexService;

import java.math.BigDecimal;

public abstract class BitmexContractTypeBtcEx extends BitmexContractTypeEx {

    public String getFirstCurrency() {
        return "BTC";
    }

    public abstract String getSecondCurrency();

    @Override
    public BigDecimal getTickSize() {
        return BigDecimal.valueOf(0.5);
    }

    @Override
    public Integer getScale() {
        return 1;
    }

    @Override
    public boolean isEth() {
        return false;
    }

    @Override
    public String toString() {
        return getFirstCurrency() + getSecondCurrency();
    }

    @Override
    public String getMarketName() {
        return BitmexService.NAME;
    }
}
