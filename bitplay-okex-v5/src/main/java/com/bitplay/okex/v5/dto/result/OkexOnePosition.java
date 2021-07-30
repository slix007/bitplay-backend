package com.bitplay.okex.v5.dto.result;

import java.util.List;
import java.util.Optional;
import lombok.Data;

@Data
public class OkexOnePosition {

    Boolean result; // true
    List<OkexPosition> holding;

    public Optional<OkexPosition> getOne() {
        return holding.stream()
                .findFirst();
    }

}