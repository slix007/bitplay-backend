package com.bitplay.persistance.domain.borders;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Created by Sergey Shurmin on 4/1/18.
 */
@Getter
@Setter
@NoArgsConstructor
public class BorderDelta {

    private DeltaCalcType deltaCalcType;

    /**
     * delta_past - период времени от настоящего момента в прошлое, в течение которого берем значения дельт (3600 sec)
     */
    private Integer deltaCalcPast;

    private DeltaSaveType deltaSaveType;

    /**
     * Only for {@link DeltaSaveType#DEVIATION}
     *
     * Добавить на UI параметр delta_dev. Параметр задается в USD, параметр общий для b_delta и o_delta.
     * Если abs(b_delta2) - abs(b_delta1) > delta_dev, то мы записываем b_detla2 в БД,
     * в противном случае нет
     * (b_delta1 - последняя записанная в БД дельта, b_delta2 - текущая дельта).
     *
     * Аналогично с Okex: если abs(o_delta2) - abs(o_delta1) > delta_dev, то мы записываем o_detla2 в БД.
     * (b_delta1 - последняя записанная в БД дельта, b_delta2 - текущая дельта),
     * в противном случае нет.
     * Если delta_dev = 0, то берем все дельты в БД.
     */
    private Integer deltaSaveDev;

    /**
     * Some DeltaSaveType do not use it.
     */
    private Integer deltaSavePerSec;

    public static BorderDelta createDefault() {
        final BorderDelta borderDelta = new BorderDelta();
        borderDelta.deltaCalcType = DeltaCalcType.DELTA;
        borderDelta.deltaCalcPast = 3600;
        borderDelta.deltaSaveType = DeltaSaveType.DEVIATION;
        borderDelta.deltaSaveDev = 1;
        borderDelta.deltaSavePerSec = 60;
        return borderDelta;
    }

    public enum DeltaCalcType {DELTA, AVG_DELTA}
    public enum DeltaSaveType {DEVIATION, PERIODIC, ALL}
}
