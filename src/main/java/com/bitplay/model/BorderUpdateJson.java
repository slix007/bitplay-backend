package com.bitplay.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Created by Sergey Shurmin on 4/19/17.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class BorderUpdateJson {
    private String border1;
    private String border2;

    public BorderUpdateJson() {
    }

    public String getBorder1() {
        return border1;
    }

    public void setBorder1(String border1) {
        this.border1 = border1;
    }

    public String getBorder2() {
        return border2;
    }

    public void setBorder2(String border2) {
        this.border2 = border2;
    }
}
