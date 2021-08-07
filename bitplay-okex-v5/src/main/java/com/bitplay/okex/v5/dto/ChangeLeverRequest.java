package com.bitplay.okex.v5.dto;

import lombok.Data;

@Data
public class ChangeLeverRequest {

    private final String instId;
    private final String lever;
    // isolated cross
    private final String mgnMode = "cross";
}
