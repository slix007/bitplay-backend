package com.bitplay.model;

import java.math.BigDecimal;
import lombok.Getter;

/**
 * Created by Sergey Shurmin on 6/28/17.
 */
@Getter
public class Pos {

    private BigDecimal positionLong;
    private BigDecimal positionShort;
    private BigDecimal longAvailToClose;
    private BigDecimal shortAvailToClose;
    private BigDecimal leverage;
    private BigDecimal liquidationPrice;
    private BigDecimal priceAvgLong;
    private BigDecimal priceAvgShort;
    private BigDecimal markValue;
    private String raw;

    public Pos(BigDecimal positionLong, BigDecimal positionShort, BigDecimal leverage, BigDecimal liquidationPrice, String raw) {
        this.positionLong = positionLong;
        this.positionShort = positionShort;
        this.leverage = leverage;
        this.liquidationPrice = liquidationPrice;
        this.raw = raw;
    }

    public Pos(BigDecimal positionLong, BigDecimal positionShort, BigDecimal leverage, BigDecimal liquidationPrice, BigDecimal markValue, String raw) {
        this.positionLong = positionLong;
        this.positionShort = positionShort;
        this.leverage = leverage;
        this.liquidationPrice = liquidationPrice;
        this.markValue = markValue;
        this.raw = raw;
    }

    public Pos(BigDecimal positionLong, BigDecimal positionShort,
            BigDecimal leverage, BigDecimal liquidationPrice, BigDecimal markValue,
            BigDecimal priceAvgLong, BigDecimal priceAvgShort, String raw) {
        this.positionLong = positionLong;
        this.positionShort = positionShort;
        this.leverage = leverage;
        this.liquidationPrice = liquidationPrice;
        this.markValue = markValue;
        this.priceAvgLong = priceAvgLong;
        this.priceAvgShort = priceAvgShort;
        this.raw = raw;
    }

    public Pos(BigDecimal positionLong, BigDecimal positionShort,
            BigDecimal longAvailToClose, BigDecimal shortAvailToClose,
            BigDecimal leverage, BigDecimal liquidationPrice, BigDecimal markValue,
            BigDecimal priceAvgLong, BigDecimal priceAvgShort, String raw) {
        this.positionLong = positionLong;
        this.positionShort = positionShort;
        this.longAvailToClose = longAvailToClose;
        this.shortAvailToClose = shortAvailToClose;
        this.leverage = leverage;
        this.liquidationPrice = liquidationPrice;
        this.markValue = markValue;
        this.priceAvgLong = priceAvgLong;
        this.priceAvgShort = priceAvgShort;
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
                ", raw='" + raw + '\'' +
                '}';
    }

}
