package com.bitplay.market.bitmex;

import org.knowm.xchange.dto.account.Position;

/**
 * Created by Sergey Shurmin on 11/26/17.
 */
public class BitmexUtils {

    public static String positionToString(Position position) {
        return "Position{" +
                "position=" + position.getPositionLong() +
                ", leverage=" + position.getLeverage() +
                ", liquidationPrice=" + position.getLiquidationPrice() +
                ", priceAvg=" + position.getPriceAvgLong() +
                ", markValue=" + position.getMarkValue() +
                '}';
    }
}
