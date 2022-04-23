package com.bitplay.market.model

import com.bitplay.api.dto.ob.FundingRateBordersBlock
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalTime

data class OkexFunding(
    @Volatile var ff: Block = Block(),
    @Volatile var sf: Block = Block(),
) {
    fun toFundingRateBordersBlock(f:  FundingRateBordersBlock.Timer, s: FundingRateBordersBlock.Timer): FundingRateBordersBlock =
        FundingRateBordersBlock(
            ff.toFundingRateBordersBlock(f),
            sf.toFundingRateBordersBlock(s)
        )

    data class Block(
        val rate: BigDecimal? = null,
        val costBtc: BigDecimal? = null,
        val costUsd: BigDecimal? = null,
        val costPts: BigDecimal? = null,
    ) {
        fun toFundingRateBordersBlock(timer:  FundingRateBordersBlock.Timer) =
            FundingRateBordersBlock.Block(
                this.rate?.toPlainString() ?: "",
                this.costBtc?.toPlainString() ?: "",
                this.costUsd?.toPlainString() ?: "",
                this.costPts?.toPlainString() ?: "",
                timer
            )
    }
}