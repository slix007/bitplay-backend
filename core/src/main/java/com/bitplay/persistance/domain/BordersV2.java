package com.bitplay.persistance.domain;

import java.util.List;

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
}
