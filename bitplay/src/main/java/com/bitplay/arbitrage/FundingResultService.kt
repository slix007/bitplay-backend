package com.bitplay.arbitrage

import com.bitplay.api.dto.ob.FundingResultBlock
import com.bitplay.market.bitmex.BitmexService
import com.bitplay.market.okcoin.OkCoinService
import com.bitplay.persistance.SettingsRepositoryService
import com.bitplay.persistance.domain.settings.BitmexContractTypeEx
import com.bitplay.utils.SchedulerUtils
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Формула: Funding result,
 * pts = ((b_FFrate_cost_pts * b_FF_share)
 *      + (b_SFrate_cost_pts * b_SF_share))
 *      - ((o_FFrate_cost_pts * o_FF_share)
 *      + (o_SFrate_cost_pts * o_SF_share)), где
b_FFrate_cost_pts, b_SFrate_cost_pts, o_FFrate_cost_pts, o_SFrate_cost_pts = cost,
pts для First Funding rate и Second Funding rate (см. https://trello.com/c/aP4Dlq08).
b_FF_share = (b_FF_SCB - b_FF_TimeLeft) / b_FF_SCB;
b_SF_share = (b_SF_SCB - b_SF_TimeLeft) / b_SF_SCB;
o_FF_share = (o_FF_SCB - o_FF_TimeLeft) / o_FF_SCB;
o_SF_share = (o_SF_SCB - o_SF_TimeLeft) / o_SF_SCB.
 */
@Service
class FundingResultService(
    private val settingsRepositoryService: SettingsRepositoryService,
    private val arbitrageService: ArbitrageService,
    private val fundingTimerService: FundingTimerService,
) {
    @Volatile
    private var fundingResult: BigDecimal = BigDecimal.ZERO

    @Volatile
    private var bFfrateCostPts: BigDecimal = BigDecimal.ZERO

    @Volatile
    private var bSfrateCostPts: BigDecimal = BigDecimal.ZERO

    @Volatile
    private var oFfrateCostPts: BigDecimal = BigDecimal.ZERO

    @Volatile
    private var oSfrateCostPts: BigDecimal = BigDecimal.ZERO

    @Volatile
    private var bFfShare: BigDecimal = BigDecimal.ZERO

    @Volatile
    private var bSfShare: BigDecimal = BigDecimal.ZERO

    @Volatile
    private var oFfShare: BigDecimal = BigDecimal.ZERO

    @Volatile
    private var oSfShare: BigDecimal = BigDecimal.ZERO

    // На UI вывести в виде суммы слагаемых, например:
    //Funding result = (2.24*0.33)+(2.12*0.01)+(3.12*0.7)+(2.91*0.03) = 1.45.

    private var executor = SchedulerUtils.singleThreadExecutor("fundingResultSetter-%d")

    fun runCalc() {
        executor.execute { calc() }
    }

    private fun calc() {
        if (arbitrageService.areBothOkex()) {
            return
        }
        val settings = settingsRepositoryService.settings
        if (settings.shouldStopCalculateFundingResult()) {
            return
        }

        if (fundingTimerService.noOneGreen()
            || !settings.fundingSettings.fundingResultEnabled
        ) {
            bFfrateCostPts = BigDecimal.ZERO
            bSfrateCostPts = BigDecimal.ZERO
            oFfrateCostPts = BigDecimal.ZERO
            oSfrateCostPts = BigDecimal.ZERO
            bFfShare = BigDecimal.ZERO
            bSfShare = BigDecimal.ZERO
            oFfShare = BigDecimal.ZERO
            oSfShare = BigDecimal.ZERO
            fundingResult = BigDecimal.ZERO
            return
        }
        val bitmex = arbitrageService.leftMarketService as BitmexService
        val scale = BitmexContractTypeEx.getFundingScale(bitmex.bitmexContractTypeEx.currencyPair.base.currencyCode)
        val bitmexFunding = bitmex.bitmexSwapService.bitmexFunding
        val b_FFrate_cost_pts = bitmexFunding.fundingCostPts
        val b_SFrate_cost_pts = bitmexFunding.sfCostPts
        val okex = arbitrageService.rightMarketService as OkCoinService
        val o_FFrate_cost_pts = okex.okexFunding.ff.costPts
        val o_SFrate_cost_pts = okex.okexFunding.sf.costPts

        if (b_FFrate_cost_pts == null
            || b_SFrate_cost_pts == null
            || o_FFrate_cost_pts == null
            || o_SFrate_cost_pts == null
        ) {
            bFfrateCostPts = BigDecimal.ZERO
            bSfrateCostPts = BigDecimal.ZERO
            oFfrateCostPts = BigDecimal.ZERO
            oSfrateCostPts = BigDecimal.ZERO
            bFfShare = BigDecimal.ZERO
            bSfShare = BigDecimal.ZERO
            oFfShare = BigDecimal.ZERO
            oSfShare = BigDecimal.ZERO
            fundingResult = BigDecimal.ZERO
            return
        }

        //b_FF_share = (b_FF_SCB - b_FF_TimeLeft) / b_FF_SCB;
        //b_SF_share = (b_SF_SCB - b_SF_TimeLeft) / b_SF_SCB;
        //o_FF_share = (o_FF_SCB - o_FF_TimeLeft) / o_FF_SCB;
        //o_SF_share = (o_SF_SCB - o_SF_TimeLeft) / o_SF_SCB.
        val b_FF_SCB = settings.fundingSettings.leftFf.scbSec
        val b_SF_SCB = settings.fundingSettings.leftSf.scbSec
        val o_FF_SCB = settings.fundingSettings.rightFf.scbSec
        val o_SF_SCB = settings.fundingSettings.rightSf.scbSec
        val b_FF_TimeLeft = fundingTimerService.getSecToRunLff().toLong()
        val b_SF_TimeLeft = fundingTimerService.getSecToRunLsf().toLong()
        val o_FF_TimeLeft = fundingTimerService.getSecToRunRff().toLong()
        val o_SF_TimeLeft = fundingTimerService.getSecToRunRsf().toLong()
        val b_FF_share = BigDecimal(b_FF_SCB - b_FF_TimeLeft).divide(BigDecimal(b_FF_SCB), scale, RoundingMode.HALF_UP)
        val b_SF_share = BigDecimal(b_SF_SCB - b_SF_TimeLeft).divide(BigDecimal(b_SF_SCB), scale, RoundingMode.HALF_UP)
        val o_FF_share = BigDecimal(o_FF_SCB - o_FF_TimeLeft).divide(BigDecimal(o_FF_SCB), scale, RoundingMode.HALF_UP)
        val o_SF_share = BigDecimal(o_SF_SCB - o_SF_TimeLeft).divide(BigDecimal(o_SF_SCB), scale, RoundingMode.HALF_UP)


//        var b_FFrate_cost_pts1: BigDecimal = BigDecimal.ZERO
//        var b_SFrate_cost_pts1: BigDecimal = BigDecimal.ZERO
//        var o_FFrate_cost_pts1: BigDecimal = BigDecimal.ZERO
//        var o_SFrate_cost_pts1: BigDecimal = BigDecimal.ZERO
        var b_FF_share1: BigDecimal = BigDecimal.ZERO
        var b_SF_share1: BigDecimal = BigDecimal.ZERO
        var o_FF_share1: BigDecimal = BigDecimal.ZERO
        var o_SF_share1: BigDecimal = BigDecimal.ZERO
        if (fundingTimerService.isGreenTime("leftFf")) {
//            b_FFrate_cost_pts1 = b_FFrate_cost_pts
            b_FF_share1 = b_FF_share
        }
        if (fundingTimerService.isGreenTime("leftSf")) {
//            b_SFrate_cost_pts1 = b_SFrate_cost_pts
            b_SF_share1 = b_SF_share
        }
        if (fundingTimerService.isGreenTime("rightFf")) {
//            o_FFrate_cost_pts1 = o_FFrate_cost_pts
            o_FF_share1 = o_FF_share
        }
        if (fundingTimerService.isGreenTime("rightSf")) {
//            o_SFrate_cost_pts1 = o_SFrate_cost_pts
            o_SF_share1 = o_SF_share
        }
        // pts = ((b_FFrate_cost_pts * b_FF_share)
        // *      + (b_SFrate_cost_pts * b_SF_share))
        // *      - ((o_FFrate_cost_pts * o_FF_share)
        // *      + (o_SFrate_cost_pts * o_SF_share))
        val pts = ((b_FFrate_cost_pts.multiply(b_FF_share1))
            .add(
                (b_SFrate_cost_pts.multiply(b_SF_share1)
                        ).subtract(
                        o_FFrate_cost_pts.multiply(o_FF_share1)
                            .add(
                                o_SFrate_cost_pts.multiply(o_SF_share1)
                            )
                    )
            ))

        bFfrateCostPts = b_FFrate_cost_pts
        bSfrateCostPts = b_SFrate_cost_pts
        oFfrateCostPts = o_FFrate_cost_pts
        oSfrateCostPts = o_SFrate_cost_pts
        bFfShare = b_FF_share1
        bSfShare = b_SF_share1
        oFfShare = o_FF_share1
        oSfShare = o_SF_share1
        fundingResult = pts.setScale(scale, RoundingMode.HALF_UP)
    }

    fun getFundingResultBlock(): FundingResultBlock =
        FundingResultBlock(
            fundingResult.toPlainString(),
            """
(($bFfrateCostPts*$bFfShare) + ($bSfrateCostPts*$bSfShare) - (($oFfrateCostPts*$oFfShare) + ($oSfrateCostPts*$oSfShare)) = $fundingResult
            """.trimIndent()
        )

    fun getFundingResult() = fundingResult

}