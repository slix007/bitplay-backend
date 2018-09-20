package com.bitplay.api.domain.ob;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ContractExtraJson {

    private String ethBtcBal;
    private String bxbtBal;

    public static ContractExtraJson empty() {
        return new ContractExtraJson();
    }

}
