package com.bitplay.api.domain;

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

    private Map<Instant, Long> btmDeltas;
    private Map<Instant, Long> okDeltas;
}
