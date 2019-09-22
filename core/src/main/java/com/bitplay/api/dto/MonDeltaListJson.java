package com.bitplay.api.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class MonDeltaListJson {

    private Map<Instant, BigDecimal> btmDeltas;
    private Map<Instant, BigDecimal> okDeltas;
}
