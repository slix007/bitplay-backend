package com.bitplay.model;

/**
 * Created by Sergey Shurmin on 4/18/17.
 */
public class DeltasJson {

    private String delta1;
    private String delta2;

    public DeltasJson(String delta1, String delta2) {
        this.delta1 = delta1;
        this.delta2 = delta2;
    }

    public String getDelta1() {
        return delta1;
    }

    public String getDelta2() {
        return delta2;
    }
}
