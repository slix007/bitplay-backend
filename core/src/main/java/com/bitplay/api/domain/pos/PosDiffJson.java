package com.bitplay.api.domain.pos;

import com.bitplay.persistance.domain.settings.PlacingBlocks;
import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PosDiffJson {

    private boolean mainSetEqual;
    private String mainSetStr;
    private String mainSetSource;
    private boolean extraSetEqual;
    private String extraSetStr;
    private String extraSetSource;

    private PlacingBlocks placingBlocks;
    private String btmUsdInContract;
    private Boolean isEth;
    private BigDecimal cm;

    public static PosDiffJson notInitialized() {
        return new PosDiffJson(true, "position is not yet initialized", null,
                false, "position is not yet initialized", null, null, null, null, null);
    }
//    private String okUsdInContract;

}
