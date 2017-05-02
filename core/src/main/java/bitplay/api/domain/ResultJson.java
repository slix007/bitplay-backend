package com.bitplay.api.domain;

/**
 * Created by Sergey Shurmin on 4/24/17.
 */
public class ResultJson {
    private String resutl;
    private String description;

    public ResultJson(String resutl, String description) {
        this.resutl = resutl;
        this.description = description;
    }

    public String getResutl() {
        return resutl;
    }

    public void setResutl(String resutl) {
        this.resutl = resutl;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }
}
