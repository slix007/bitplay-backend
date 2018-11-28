package com.bitplay.market;

import com.bitplay.persistance.domain.settings.AmountType;
import com.bitplay.market.model.PlaceOrderArgs;
import java.math.BigDecimal;
import java.math.RoundingMode;

public class MarketUtils {

    public static BigDecimal amountInContracts(PlaceOrderArgs placeOrderArgs, BigDecimal cm) {
        AmountType amountType = placeOrderArgs.getAmountType() != null
                ? placeOrderArgs.getAmountType()
                : AmountType.CONT;
        if (amountType == AmountType.CONT) {
            return placeOrderArgs.getAmount();
        }
        // convert usd to cont
        BigDecimal usd = placeOrderArgs.getAmount();
        if (placeOrderArgs.getContractType().isEth()) { // bitmex: 10 / CM USD = 1 cont,
            return BigDecimal.valueOf(10).multiply(usd).divide(cm, 0, RoundingMode.HALF_UP);
        }
        return usd;
    }

}