package com.bitplay.persistance.domain.borders;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Transient;

import java.math.BigDecimal;
import java.util.Objects;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class BorderItem {

    private int id;
    private BigDecimal value;// valueSource
    @Transient
    volatile private BigDecimal valueFinal;
    private int posLongLimit;
    private int posShortLimit;

    public BorderItem(int id, BigDecimal value, int posLongLimit, int posShortLimit) {
        this.id = id;
        this.value = value;
        this.posLongLimit = posLongLimit;
        this.posShortLimit = posShortLimit;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, value, posLongLimit, posShortLimit);
    }
}
