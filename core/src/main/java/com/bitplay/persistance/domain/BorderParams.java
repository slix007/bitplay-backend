package com.bitplay.persistance.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.TypeAlias;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.List;

/**
 * Created by Sergey Shurmin on 7/18/17.
 */
@Document(collection = "bordersCollection")
@TypeAlias("borders")
public class BorderParams {

    @Id
    private String borderName;
    private List<BorderItem> borderItemList;

    public BorderParams() {
    }

    public BorderParams(String borderName, List<BorderItem> borderItemList) {
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
}
