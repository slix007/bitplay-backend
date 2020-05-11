package com.bitplay.persistance.domain.settings.contracttype;

import com.bitplay.market.bitmex.BitmexService;
import com.bitplay.persistance.domain.settings.ContractType;

public abstract class BitmexContractTypeEx implements ContractType {

    @Override
    public String getMarketName() {
        return BitmexService.NAME;
    }
}
