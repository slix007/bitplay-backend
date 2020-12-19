package com.bitplay.model;

import lombok.Getter;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Created by Sergey Shurmin on 6/28/17.
 */
@Getter
public class Pos {

    private final BigDecimal positionLong;
    private final BigDecimal positionShort;
    private final BigDecimal longAvailToClose;
    private final BigDecimal shortAvailToClose;
    private final BigDecimal leverage;
    private final BigDecimal liquidationPrice;
    private final BigDecimal priceAvgLong;
    private final BigDecimal priceAvgShort;
    private final BigDecimal markValue; // bitmex only
    private final Instant timestamp;
    private final String raw;
    private final BigDecimal plPos; // okex only:

    private Pos(BigDecimal positionLong, BigDecimal positionShort, BigDecimal leverage, BigDecimal liquidationPrice) {
        this.positionLong = positionLong;
        this.positionShort = positionShort;
        this.leverage = leverage;
        this.liquidationPrice = liquidationPrice;
        this.longAvailToClose = null;
        this.shortAvailToClose = null;
        this.priceAvgLong = null;
        this.priceAvgShort = null;
        this.markValue = null;
        this.timestamp = null;
        this.raw = null;
        this.plPos = null;
    }

    public static Pos emptyPos() {
        return new Pos(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, null, "position is empty",
                null);
    }

    //TODO refactor to emptyPos => use emptyPos on init => remove in Bitmex#mergePostition emptyPos() call
    public static Pos nullPos() {
        return new Pos(null, null, null, BigDecimal.ZERO);
    }

    public static Pos posForTests(BigDecimal posLong) {
        return new Pos(posLong, null, null, BigDecimal.ZERO);
    }

    public Pos(BigDecimal positionLong, BigDecimal positionShort,
            BigDecimal longAvailToClose, BigDecimal shortAvailToClose,
            BigDecimal leverage, BigDecimal liquidationPrice, BigDecimal markValue,
            BigDecimal priceAvgLong, BigDecimal priceAvgShort, Instant timestamp, String raw, BigDecimal plPos) {
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
        this.plPos = plPos;
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

    public Pos updatePlPos(BigDecimal plPos) {
        return new Pos(
                this.positionLong,
                this.positionShort,
                this.longAvailToClose,
                this.shortAvailToClose,
                this.leverage,
                this.liquidationPrice,
                this.markValue,
                this.priceAvgLong,
                this.priceAvgShort,
                this.timestamp,
                this.raw,
                plPos);
    }
}
