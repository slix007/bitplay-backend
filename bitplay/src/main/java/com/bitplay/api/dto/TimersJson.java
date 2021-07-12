package com.bitplay.api.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class TimersJson {

    private String startSignalTimerStr;
    private String deltaMinTimerStr;
    private String bordersTimerStr;
    private String bordersV2TableHashCode;

}
