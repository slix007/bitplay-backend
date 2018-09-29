package com.bitplay.api.domain.pos;

import com.bitplay.persistance.domain.settings.PlacingBlocks;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PosDiffJson {

    private boolean equal;
    private String str;

    private PlacingBlocks placingBlocks;

    private String notionalSource;
    private String btmUsdInContract;
//    private String okUsdInContract;

}
