package com.bitplay.persistance.domain;

import java.math.BigDecimal;

/**
 * Created by Sergey Shurmin on 10/5/17.
 */
public class BorderItem {

    private int id;
    private int value;
    private int posLongLimit;
    private int posShortLimit;

    public BorderItem() {
    }

    public BorderItem(int id, int value, int posLongLimit, int posShortLimit) {
        this.id = id;
        this.value = value;
        this.posLongLimit = posLongLimit;
        this.posShortLimit = posShortLimit;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public int getValue() {
        return value;
    }

    public void setValue(int value) {
        this.value = value;
    }

    public int getPosLongLimit() {
        return posLongLimit;
    }

    public void setPosLongLimit(int posLongLimit) {
        this.posLongLimit = posLongLimit;
    }

    public int getPosShortLimit() {
        return posShortLimit;
    }

    public void setPosShortLimit(int posShortLimit) {
        this.posShortLimit = posShortLimit;
    }
}
