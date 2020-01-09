package com.bitplay.arbitrage.posdiff;

import com.bitplay.arbitrage.dto.SignalType;
import com.bitplay.market.MarketServicePreliq;
import com.bitplay.persistance.domain.settings.ContractType;
import lombok.Data;
import org.knowm.xchange.dto.Order;

import java.math.BigDecimal;

@Data
public class CorrObj {

    CorrObj(SignalType signalType, BigDecimal oPL, BigDecimal oPS) {
        this.signalType = signalType;
        this.oPL = oPL;
        this.oPS = oPS;
    }

    SignalType signalType;
    Order.OrderType orderType;
    BigDecimal correctAmount;
    MarketServicePreliq marketService;
    ContractType contractType;
    String errorDescription;

    boolean okexThroughZero = false;
    BigDecimal oPL, oPS;
}
