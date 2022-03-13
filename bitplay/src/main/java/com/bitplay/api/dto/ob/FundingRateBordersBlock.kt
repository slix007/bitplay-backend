package com.bitplay.api.dto.ob

data class FundingRateBordersBlock(
        val ff: Block = Block(),
        val sf: Block = Block(),
) {
    data class Block(
            val rate: String = "0",
            val costBtc: String = "0",
            val costUsd: String = "0",
            val costPts: String = "0"
    )
}