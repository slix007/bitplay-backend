package com.bitplay.okex.v5.dto.result;

import java.util.List;
import lombok.Data;

@Data
public class OkexSwapAllPositions {

    Boolean result; // true
    List<List<OkexSwapPosition>> holding;

}