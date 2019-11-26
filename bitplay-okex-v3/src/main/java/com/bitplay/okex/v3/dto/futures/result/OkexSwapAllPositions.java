package com.bitplay.okex.v3.dto.futures.result;

import lombok.Data;

import java.util.List;

@Data
public class OkexSwapAllPositions {

    Boolean result; // true
    List<List<OkexSwapPosition>> holding;

}
