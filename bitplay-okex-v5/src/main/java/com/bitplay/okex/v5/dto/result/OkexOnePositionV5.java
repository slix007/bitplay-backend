package com.bitplay.okex.v5.dto.result;

import java.util.List;
import java.util.Optional;
import lombok.Data;

@Data
public class OkexOnePositionV5 {

    private String code;
    private String msg;
    private List<OkexPosV5> data;

    public Optional<OkexPosV5> getOne() {
        return data.stream()
                .findFirst();
    }

}
