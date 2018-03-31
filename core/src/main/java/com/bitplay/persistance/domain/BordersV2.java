package com.bitplay.persistance.domain;

import org.springframework.data.annotation.Transient;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Created by Sergey Shurmin on 10/9/17.
 */
public class BordersV2 {
    private List<BorderTable> borderTableList;
    private Integer maxLvl;
    private Boolean autoBaseLvl;
    private Integer baseLvlCnt; // base level, starting point
    private BaseLvlType baseLvlType;
    private Integer step; // between levels
    private Integer gapStep; // between tables
    private BigDecimal bAddDelta;
    private BigDecimal okAddDelta;
    @Transient
    private int borderTableHashCode;

    /**
     * in use as json object by API
     */
    public BordersV2() {
    }

    public BordersV2(List<BorderTable> borderTableList) {
        this.borderTableList = borderTableList;
    }

    public List<BorderTable> getBorderTableList() {
        return borderTableList;
    }

    public void setBorderTableList(List<BorderTable> borderTableList) {
        this.borderTableList = borderTableList;
    }

    public Optional<BorderTable> getBorderTableByName(String borderName) {
        final List<BorderTable> borderTableList = getBorderTableList();
        return borderTableList.stream()
                .filter(borderTable -> borderTable.getBorderName().equals(borderName))
                .findFirst();
    }

    public Integer getMaxLvl() {
        return maxLvl;
    }

    public void setMaxLvl(Integer maxLvl) {
        this.maxLvl = maxLvl;
    }

    public Boolean getAutoBaseLvl() {
        return autoBaseLvl;
    }

    public void setAutoBaseLvl(Boolean autoBaseLvl) {
        this.autoBaseLvl = autoBaseLvl;
    }

    public Integer getBaseLvlCnt() {
        return baseLvlCnt;
    }

    public void setBaseLvlCnt(Integer baseLvlCnt) {
        this.baseLvlCnt = baseLvlCnt;
    }

    public BaseLvlType getBaseLvlType() {
        return baseLvlType;
    }

    public void setBaseLvlType(BaseLvlType baseLvlType) {
        this.baseLvlType = baseLvlType;
    }

    public Integer getStep() {
        return step;
    }

    public void setStep(Integer step) {
        this.step = step;
    }

    public Integer getGapStep() {
        return gapStep;
    }

    public void setGapStep(Integer gapStep) {
        this.gapStep = gapStep;
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

    public enum BaseLvlType {B_OPEN, OK_OPEN}

    public int getBorderTableHashCode() {
        return Objects.hash(borderTableList);
    }
}
