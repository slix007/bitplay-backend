package com.bitplay.api.dto.states;

import java.math.BigDecimal;
import lombok.Data;

@Data
public class AutoAddBorderJson {

    private final BigDecimal borderCrossDepth;
    private final BigDecimal leftAddBorder;
    private final BigDecimal rightAddBorder;

}
