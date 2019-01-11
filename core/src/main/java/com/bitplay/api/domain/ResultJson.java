package com.bitplay.api.domain;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Created by Sergey Shurmin on 4/24/17.
 */

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ResultJson {
    private String result;
    private String description;
    private Object object;

    public ResultJson(String result, String description) {
        this.result = result;
        this.description = description;
    }
}
