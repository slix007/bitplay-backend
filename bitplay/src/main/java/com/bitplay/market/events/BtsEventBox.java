package com.bitplay.market.events;

import javax.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/**
 * Created by Sergey Shurmin on 6/6/17.
 */
@Getter
@Setter
public class BtsEventBox {

    @NotNull
    private final BtsEvent btsEvent;

    private Long tradeId;

    public BtsEventBox(@NotNull BtsEvent btsEvent, Long tradeId) {
        this.btsEvent = btsEvent;
        this.tradeId = tradeId;
    }
}
