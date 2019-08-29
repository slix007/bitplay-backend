package com.bitplay.market;

import com.bitplay.model.Pos;
import com.bitplay.persistance.domain.settings.AmountType;
import com.bitplay.market.model.PlaceOrderArgs;
import java.math.BigDecimal;
import java.math.RoundingMode;
import org.knowm.xchange.dto.account.Position;

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

    public static Pos mapPos(Position position) {
        return new Pos(position.getPositionLong(),
                position.getPositionShort(),
                position.getLongAvailToClose(),
                position.getShortAvailToClose(),
                position.getLeverage(),
                position.getLiquidationPrice(),
                position.getMarkValue(),
                position.getPriceAvgLong(),
                position.getPriceAvgShort(),
                null,
                position.getRaw());
    }

}
