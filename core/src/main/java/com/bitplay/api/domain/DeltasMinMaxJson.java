package com.bitplay.api.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Created by Sergey Shurmin on 7/17/17.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@Getter
@AllArgsConstructor
public class DeltasMinMaxJson {

    private MinMaxData instantDelta;
    private MinMaxData deltaMin;
    private SignalData signalData;

    public enum Color {
        BLACK,
        ORANGE,
    }

    @Getter
    public static class MinMaxData {

        private String btmDeltaMin;
        private String okDeltaMin;
        private String btmDeltaMax;
        private String okDeltaMax;
        private Color btmMaxColor;
        private Color okMaxColor;

        public MinMaxData(String bDeltaMin, String oDeltaMin, String bDeltaMax, String oDeltaMax) {
            this.btmDeltaMin = bDeltaMin;
            this.okDeltaMin = oDeltaMin;
            this.btmDeltaMax = bDeltaMax;
            this.okDeltaMax = oDeltaMax;
        }

        public MinMaxData(String bDeltaMin, String oDeltaMin, String bDeltaMax, String oDeltaMax, Instant bLastRise, Instant oLastRise) {
            this.btmDeltaMin = bDeltaMin;
            this.okDeltaMin = oDeltaMin;
            this.btmDeltaMax = bDeltaMax;
            this.okDeltaMax = oDeltaMax;
            Instant currTime = Instant.now();
            this.btmMaxColor = bLastRise == null || bLastRise.plusSeconds(120).isAfter(currTime)
                    ? Color.ORANGE
                    : Color.BLACK;
            this.okMaxColor = oLastRise == null || oLastRise.plusSeconds(120).isAfter(currTime)
                    ? Color.ORANGE
                    : Color.BLACK;
        }
    }

    @Getter
    public static class SignalData {

        private String signalTimeMin;
        private String signalTimeMax;
        private String signalTimeAvg;
        private Color maxColor;

        public SignalData(String signalTimeMin, String signalTimeMax, String signalTimeAvg, Instant maxLastRise) {
            this.signalTimeMin = signalTimeMin;
            this.signalTimeMax = signalTimeMax;
            this.signalTimeAvg = signalTimeAvg;
            Instant currTime = Instant.now();
            this.maxColor = maxLastRise == null || maxLastRise.plusSeconds(120).isAfter(currTime)
                    ? Color.ORANGE
                    : Color.BLACK;
        }

    }
}
