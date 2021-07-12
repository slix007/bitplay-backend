package com.bitplay.api.dto;

import com.bitplay.persistance.domain.CumParams;
import com.bitplay.persistance.domain.CumTimeType;
import com.bitplay.persistance.domain.CumType;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class CumParamsRequestJson {

    private CumType cumType;
    private CumTimeType cumTimeType;

    private CumParams cumParams;
}
