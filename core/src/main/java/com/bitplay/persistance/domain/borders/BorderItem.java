package com.bitplay.persistance.domain.borders;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Created by Sergey Shurmin on 10/5/17.
 */
public class BorderItem {

    private int id;
    private BigDecimal value;
    private int posLongLimit;
    private int posShortLimit;

    public BorderItem() {
    }

    public BorderItem(int id, BigDecimal value, int posLongLimit, int posShortLimit) {
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

    public BigDecimal getValue() {
        return value;
    }

    public void setValue(BigDecimal value) {
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

    @Override
    public String toString() {
        return "{" +
                "id=" + id +
                ",val=" + value +
                ",pLL=" + posLongLimit +
                ",pSL=" + posShortLimit +
                '}';
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, value, posLongLimit, posShortLimit);
    }
}
