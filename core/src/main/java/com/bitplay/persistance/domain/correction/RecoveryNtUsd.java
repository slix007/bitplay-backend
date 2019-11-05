package com.bitplay.persistance.domain.correction;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@JsonIgnoreProperties(ignoreUnknown = true)
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RecoveryNtUsd {
    private Integer maxBlockUsd;

    public static RecoveryNtUsd createDefault() {
        return new RecoveryNtUsd(0);
    }

}
