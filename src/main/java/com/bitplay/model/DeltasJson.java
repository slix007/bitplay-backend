package com.bitplay.model;

/**
 * Created by Sergey Shurmin on 4/18/17.
 */
public class DeltasJson {

    private String delta1;
    private String delta2;
    private String border1;
    private String border2;

    public DeltasJson(String delta1, String delta2) {
        this.delta1 = delta1;
        this.delta2 = delta2;
    }

    public DeltasJson(String delta1, String delta2, String border1, String border2) {
        this.delta1 = delta1;
        this.delta2 = delta2;
        this.border1 = border1;
        this.border2 = border2;
    }

    public String getDelta1() {
        return delta1;
    }

    public String getDelta2() {
        return delta2;
    }

    public String getBorder1() {
        return border1;
    }

    public String getBorder2() {
        return border2;
    }
}
