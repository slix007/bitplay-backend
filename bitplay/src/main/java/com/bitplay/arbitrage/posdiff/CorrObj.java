package com.bitplay.arbitrage.posdiff;

import com.bitplay.market.MarketServicePreliq;
import com.bitplay.persistance.domain.settings.ContractType;
import com.bitplay.arbitrage.dto.SignalType;
import com.bitplay.xchange.dto.Order.OrderType;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class CorrObj {

    CorrObj(SignalType signalType, BigDecimal oPL, BigDecimal oPS) {
        this.signalType = signalType;
        this.oPL = oPL;
        this.oPS = oPS;
    }

    SignalType signalType;
    OrderType orderType;
    BigDecimal correctAmount;
    MarketServicePreliq marketService;
    ContractType contractType;
    String errorDescription;

    boolean noSwitch = false;
    boolean okexThroughZero = false;
    BigDecimal oPL, oPS;
}
