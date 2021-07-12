package com.bitplay.persistance.domain.borders;

import java.io.Serializable;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Created by Sergey Shurmin on 10/9/17.
 */
public class BorderTable implements Serializable {

    private String borderName;
    private List<BorderItem> borderItemList;

    public BorderTable() {
    }

    public BorderTable(String borderName, List<BorderItem> borderItemList) {
        this.borderName = borderName;
        this.borderItemList = borderItemList;
    }

    public String getBorderName() {
        return borderName;
    }

    public void setBorderName(String borderName) {
        this.borderName = borderName;
    }

    public List<BorderItem> getBorderItemList() {
        return borderItemList;
    }

    public void setBorderItemList(List<BorderItem> borderItemList) {
        this.borderItemList = borderItemList;
    }

    @Override
    public String toString() {
        return "BorderTable{" +
                "borderName='" + borderName + '\'' +
                ", borderItemList=" + borderItemList.stream().map(BorderItem::toString).collect(Collectors.joining(", ")) +
                '}';
    }

    @Override
    public int hashCode() {
        return Objects.hash(borderName, borderItemList);
    }
}
