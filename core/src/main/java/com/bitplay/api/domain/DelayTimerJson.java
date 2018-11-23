package com.bitplay.api.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Created by Sergey Shurmin on 6/1/17.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@Data
@AllArgsConstructor
@NoArgsConstructor
public class DelayTimerJson {

    private static final long READY_SEC = -88888;
    private static final long NONE_SEC = 88888;

    private String delaySec;

    /**
     * available values:
     * <ul>
     * <li> {any number} </li>
     * <li> _none_ </li>
     * <li> _ready_ </li>
     * </ul>
     */
    private String toSignalSec;
    /**
     * only for corr/adj/mdcCorr and their extraSets
     */
    private String activeCount;
    private List<String> activeNames;


}
