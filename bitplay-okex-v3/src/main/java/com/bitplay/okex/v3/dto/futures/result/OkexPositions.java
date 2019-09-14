package com.bitplay.okex.v3.dto.futures.result;

import com.bitplay.model.Pos;
import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import lombok.Data;

@Data
public class OkexPositions {

    Boolean result; // true
    List<List<OkexPosition>> holding;

    public Optional<OkexPosition> getByInstrumentId(String instrumentId) {
        return holding.stream()
                .flatMap(Collection::stream)
                .filter(okexPosition -> okexPosition.getInstrumentId().equals(instrumentId))
                .findFirst();
    }

    public Pos toPos(String instrumentId) {
        final Optional<OkexPosition> byInstrumentId = getByInstrumentId(instrumentId);
        if (byInstrumentId.isPresent()) {
            final OkexPosition p = byInstrumentId.get();
            return new Pos(
                    p.getLongQty(),
                    p.getShortQty(),
                    p.getLongAvailQty(),
                    p.getShortAvailQty(),
                    p.getLeverage(),
                    p.getLiquidationPrice(),
                    BigDecimal.ZERO, //mark value
                    p.getLongAvgCost(),
                    p.getShortAvgCost(),
                    p.getUpdatedAt().toInstant(),
                    p.toString(),
                    p.getLongPnl().add(p.getShortPnl())
            );
        }

        return Pos.emptyPos();
    }
}
