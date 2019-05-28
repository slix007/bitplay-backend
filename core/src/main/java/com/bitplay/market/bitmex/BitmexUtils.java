package com.bitplay.market.bitmex;

import com.bitplay.market.model.PlaceOrderArgs;
import com.bitplay.model.Pos;
import com.bitplay.persistance.domain.settings.AmountType;
import com.bitplay.persistance.domain.settings.PlacingBlocks;
import java.math.BigDecimal;

/**
 * Created by Sergey Shurmin on 11/26/17.
 */
public class BitmexUtils {

    public static String positionToString(Pos position) {
        return "Position{" +
                "position=" + position.getPositionLong() +
                ", leverage=" + position.getLeverage() +
                ", liquidationPrice=" + position.getLiquidationPrice() +
                ", priceAvg=" + position.getPriceAvgLong() +
                ", markValue=" + position.getMarkValue() +
                '}';
    }

    static BigDecimal amountInContracts(PlaceOrderArgs placeOrderArgs, BigDecimal cm) {
        if (placeOrderArgs.getAmountType() == null || placeOrderArgs.getAmountType() == AmountType.CONT) {
            return placeOrderArgs.getAmount();
        }
        return PlacingBlocks.toBitmexCont(placeOrderArgs.getAmount(), placeOrderArgs.getContractType().isEth(), cm);
    }
}
