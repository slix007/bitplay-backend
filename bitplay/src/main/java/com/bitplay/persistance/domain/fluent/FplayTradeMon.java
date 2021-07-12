package com.bitplay.persistance.domain.fluent;

import java.util.List;
import lombok.Data;

@Data
public class FplayTradeMon {

    private List<Long> bitmexPlacingMs;
    private List<Long> okexPlacingMs;

    public Long getBitmexPlacingMaxMs() {
        return bitmexPlacingMs == null ? 0L : bitmexPlacingMs.stream().max(Long::compareTo).orElse(0L);
    }

    public Long getOkexPlacingMaxMs() {
        return okexPlacingMs == null ? 0L : okexPlacingMs.stream().max(Long::compareTo).orElse(0L);
    }
}
