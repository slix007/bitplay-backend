package com.bitplay.model;

import java.math.BigDecimal;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Created by Sergey Shurmin on 6/28/17.
 */
@Getter
@AllArgsConstructor
@NoArgsConstructor
public class Pos {

    private BigDecimal positionLong;
    private BigDecimal positionShort;
    private BigDecimal longAvailToClose;
    private BigDecimal shortAvailToClose;
    private BigDecimal leverage;
    private BigDecimal liquidationPrice;
    private BigDecimal priceAvgLong;
    private BigDecimal priceAvgShort;
    private BigDecimal markValue; // bitmex only
    private Instant timestamp;
    private String raw;
    private BigDecimal plPos; // okex only:

    private Pos(BigDecimal positionLong, BigDecimal positionShort, BigDecimal leverage, BigDecimal liquidationPrice) {
        this.positionLong = positionLong;
        this.positionShort = positionShort;
        this.leverage = leverage;
        this.liquidationPrice = liquidationPrice;
    }

    public static Pos emptyPos() {
        return new Pos(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, null, "position is empty"
        );
    }

    //TODO refactor to emptyPos => use emptyPos on init => remove in Bitmex#mergePostition emptyPos() call
    public static Pos nullPos() {
        return new Pos(null, null, null, BigDecimal.ZERO);
    }

    public Pos(BigDecimal positionLong, BigDecimal positionShort,
            BigDecimal longAvailToClose, BigDecimal shortAvailToClose,
            BigDecimal leverage, BigDecimal liquidationPrice, BigDecimal markValue,
            BigDecimal priceAvgLong, BigDecimal priceAvgShort, Instant timestamp, String raw) {
        this.positionLong = positionLong;
        this.positionShort = positionShort;
        this.longAvailToClose = longAvailToClose;
        this.shortAvailToClose = shortAvailToClose;
        this.leverage = leverage;
        this.liquidationPrice = liquidationPrice;
        this.markValue = markValue;
        this.priceAvgLong = priceAvgLong;
        this.priceAvgShort = priceAvgShort;
        this.timestamp = timestamp;
        this.raw = raw;
    }

    @Override
    public String toString() {
        return "Pos{" +
                "positionLong=" + positionLong +
                ", positionShort=" + positionShort +
                ", longAvailToClose=" + longAvailToClose +
                ", shortAvailToClose=" + shortAvailToClose +
                ", leverage=" + leverage +
                ", liquidationPrice=" + liquidationPrice +
                ", priceAvgLong=" + priceAvgLong +
                ", priceAvgShort=" + priceAvgShort +
                ", markValue=" + markValue +
                ", timestamp=" + timestamp +
                ", plPos=" + plPos +
                '}';
    }

    public String toFullString() {
        return "Pos{" +
                "positionLong=" + positionLong +
                ", positionShort=" + positionShort +
                ", longAvailToClose=" + longAvailToClose +
                ", shortAvailToClose=" + shortAvailToClose +
                ", leverage=" + leverage +
                ", liquidationPrice=" + liquidationPrice +
                ", priceAvgLong=" + priceAvgLong +
                ", priceAvgShort=" + priceAvgShort +
                ", markValue=" + markValue +
                ", timestamp=" + timestamp +
                ", plPos=" + plPos +
                ", raw='" + raw + '\'' +
                '}';
    }

}
