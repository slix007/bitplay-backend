package com.bitplay.api.domain;

/**
 * Created by Sergey Shurmin on 4/24/17.
 */
public class ResultJson {
    private String result;
    private String description;

    public ResultJson(String result, String description) {
        this.result = result;
        this.description = description;
    }

    public String getResult() {
        return result;
    }

    public void setResult(String result) {
        this.result = result;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }
}
