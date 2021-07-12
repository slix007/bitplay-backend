package com.bitplay.persistance.domain.fluent;

import com.bitplay.persistance.domain.settings.TradingMode;
import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.Instant;
import java.util.Date;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TradingModeState {

    @JsonFormat(pattern = "HH:mm:ss.SSS")
    private Date timestamp;

    @NonNull
    private TradingMode tradingMode;

    public TradingModeState(TradingMode tradingMode) {
        this.timestamp = new Date(Instant.now().toEpochMilli());
        this.tradingMode = tradingMode;
    }
}
