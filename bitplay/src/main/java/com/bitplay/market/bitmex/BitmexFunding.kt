package com.bitplay.market.bitmex

import com.bitplay.arbitrage.dto.SignalType
import java.math.BigDecimal
import java.time.OffsetDateTime

/**
 * <p>
 * It is updated by swapTickerTimer.
 * </p>
 *
 * The latest info from market is in {@link BitmexContractIndex},
 *
 * <p>Created by Sergey Shurmin on 8/7/17.</p>
 */
data class BitmexFunding(
        // Fluid params on UI
        var fundingRate: BigDecimal? = null,          // from market
        var fundingCost: BigDecimal? = null,  // recalc each time from market
        var fundingCostUsd: BigDecimal? = null, // recalc each time from market
        // recalc each time from market
        var fundingCostPts: BigDecimal? = null,
        // from market
        var swapTime: OffsetDateTime? = null,
        // ticker time
        var updatingTime: OffsetDateTime? = null,

        // When swap in progress:
        // SignalType.SWAP_CLOSE_SHORT or SignalType.SWAP_CLOSE_LONG or null
        var signalType: SignalType? = null,
        // null when swap is not in progress. How much contracts we've closed to open it after.
        var startPosition: BigDecimal? = null,
        // null when swap is not in progress. Keep old swapTime until we finish swap iteration.
        var fixedSwapTime: OffsetDateTime? = null,

        // for logs
        var swapClosePrice: BigDecimal? = null,
        var swapOpenPrice: BigDecimal? = null
) {

}