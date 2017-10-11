package com.bitplay.persistance.domain;

import java.util.List;
import java.util.Optional;

/**
 * Created by Sergey Shurmin on 10/9/17.
 */
public class BordersV2 {
    private List<BorderTable> borderTableList;

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
}
