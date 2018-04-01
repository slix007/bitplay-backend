package com.bitplay.persistance.domain.borders;

import lombok.Getter;
import lombok.Setter;

/**
 * Created by Sergey Shurmin on 4/1/18.
 */
@Getter
@Setter
public class BorderDelta {

    private DeltaType deltaType;

    /**
     * delta_per - период времени от настоящего момента в прошлое, в течение которого берем значения дельт (3600 sec)
     */
    private Integer deltaPer;
    /**
     * delta_unit - периодичность в секундах добавления дельт в базу для расчета (1 sec)
     */
    private Integer deltaUnit;

    public static BorderDelta createDefault() {
        final BorderDelta borderDelta = new BorderDelta();
        borderDelta.deltaType = DeltaType.DELTA;
        borderDelta.deltaPer = 3600;
        borderDelta.deltaUnit = 1;
        return borderDelta;
    }

    public enum DeltaType {DELTA, AVG_DELTA}
}
