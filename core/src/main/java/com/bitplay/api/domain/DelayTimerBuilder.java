package com.bitplay.api.domain;

import com.bitplay.arbitrage.dto.DelayTimer;
import java.util.ArrayList;
import java.util.List;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * Created by Sergey Shurmin on 6/1/17.
 */
@Data
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class DelayTimerBuilder {

    private static final long READY_SEC = -88888;
    private static final long NONE_SEC = 88888;

    private Integer delaySec;
    private Long toSignalSec;
    private Integer activeCount;
    private List<String> activeNames;

    public static DelayTimerBuilder createEmpty(Integer delaySec) {
        return new DelayTimerBuilder(delaySec, NONE_SEC, 0, new ArrayList<>());
    }

    public DelayTimerBuilder addTimer(long timerSecToReady, String timerName) {
        Integer delaySec = this.getDelaySec();
        Long toSignalSec = this.getToSignalSec();
        Integer activeCount = this.getActiveCount();
        List<String> activeNames = this.getActiveNames();

        if (timerSecToReady != DelayTimer.NOT_STARTED) {
            activeCount++;
            activeNames.add(timerName);
            if (toSignalSec != READY_SEC) {
                if (timerSecToReady < 0) {
                    toSignalSec = READY_SEC;
                } else {
                    toSignalSec = timerSecToReady < toSignalSec ? timerSecToReady : toSignalSec; // use min of two
                }
            }
        }

        return new DelayTimerBuilder(
                delaySec,
                toSignalSec,
                activeCount,
                activeNames
        );
    }

    public DelayTimerJson toJson() {
        String toSignalSec;
        if (this.toSignalSec == NONE_SEC) {
            toSignalSec = "_none_";
        } else if (this.toSignalSec == READY_SEC) {
            toSignalSec = "_ready_";
        } else {
            toSignalSec = String.valueOf(this.toSignalSec);
        }
        return new DelayTimerJson(
                String.valueOf(this.delaySec),
                toSignalSec,
                String.valueOf(this.activeCount),
                this.activeNames
        );
    }


}
