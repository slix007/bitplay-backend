package com.bitplay.arbitrage.posdiff;

import com.bitplay.arbitrage.dto.SignalType;
import com.bitplay.market.MarketServicePreliq;
import com.bitplay.persistance.domain.settings.ContractType;
import lombok.Data;
import lombok.ToString;
import org.knowm.xchange.dto.Order;

import java.math.BigDecimal;

@Data
public class CorrObj {

    CorrObj(SignalType signalType) {
        this.signalType = signalType;
    }

    SignalType signalType;
    Order.OrderType orderType;
    BigDecimal correctAmount;
    MarketServicePreliq marketService;
    ContractType contractType;
    String errorDescription;
    boolean increasePos = false;
}
