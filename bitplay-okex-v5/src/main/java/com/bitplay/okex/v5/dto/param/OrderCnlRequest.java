package com.bitplay.okex.v5.dto.param;

import lombok.Data;

@Data
public class OrderCnlRequest {

    private final String instId;
    private final String ordId;
}
