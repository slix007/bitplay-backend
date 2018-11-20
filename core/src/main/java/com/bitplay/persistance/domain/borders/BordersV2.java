package com.bitplay.persistance.domain.borders;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Created by Sergey Shurmin on 10/9/17.
 */
@Getter
@Setter
@NoArgsConstructor
public class BordersV2 implements Serializable {
    private List<BorderTable> borderTableList;
    private Integer maxLvl;
    private Boolean autoBaseLvl;
    private Integer baseLvlCnt; // base level, starting point
    private BaseLvlType baseLvlType;
    private Integer step; // between levels
    private Integer gapStep; // between tables
    private BigDecimal bAddDelta;
    private BigDecimal okAddDelta;
    private BigDecimal plm; // position limit multiplier

    public BordersV2(List<BorderTable> borderTableList) {
        this.borderTableList = borderTableList;
    }

    public Optional<BorderTable> getBorderTableByName(String borderName) {
        final List<BorderTable> borderTableList = getBorderTableList();
        return borderTableList.stream()
                .filter(borderTable -> borderTable.getBorderName().equals(borderName))
                .findFirst();
    }

    public BigDecimal getbAddDelta() {
        return bAddDelta;
    }

    public void setbAddDelta(BigDecimal bAddDelta) {
        this.bAddDelta = bAddDelta;
    }

    public BigDecimal getOkAddDelta() {
        return okAddDelta;
    }

    public void setOkAddDelta(BigDecimal okAddDelta) {
        this.okAddDelta = okAddDelta;
    }

    public int getBorderTableHashCode() {
        return Objects.hash(borderTableList) + Objects.hash(plm);
    }

    public enum BaseLvlType {B_OPEN, OK_OPEN}
}
