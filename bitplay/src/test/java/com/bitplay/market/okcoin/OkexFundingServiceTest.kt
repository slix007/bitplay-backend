package com.bitplay.market.okcoin

import com.bitplay.arbitrage.ArbitrageService
import com.bitplay.arbitrage.FundingTimerService
import com.bitplay.persistance.SettingsRepositoryService
import com.bitplay.utils.SchedulerUtils
import org.junit.Test
import org.mockito.Mockito
import org.springframework.context.ApplicationEvent
import org.springframework.context.ApplicationEventPublisher
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.stream.IntStream
import kotlin.time.toKotlinDuration

class OkexFundingServiceTest {

    private val srs = Mockito.mock(SettingsRepositoryService::class.java)
    private val arbitrageService = Mockito.mock(ArbitrageService::class.java)

    private val applicationEventPublisher = object : ApplicationEventPublisher {
        override fun publishEvent(event: ApplicationEvent?) {
            TODO("Not yet implemented")
        }

        override fun publishEvent(event: Any?) {
            TODO("Not yet implemented")
        }
    }
    private val fundingTimerService: FundingTimerService =
        FundingTimerService(applicationEventPublisher, srs, arbitrageService)

//    @Test
//    fun nextRun() {
//        whenever(arbitrageService.areBothOkex()).thenReturn(false)
//        val okCoinService = Mockito.mock(OkCoinService::class.java)
//        whenever(arbitrageService.leftMarketService).thenReturn(okCoinService)
//        whenever(arbitrageService.rightMarketService).thenReturn(okCoinService)
//        whenever(okCoinService.isSwap).thenReturn(true)
//        whenever(srs.settings).thenReturn(
//            Settings().apply {
//                fundingSettings = FundingSettings.createDefault()
//                fundingSettings.leftFf.scbSec = 0
//                fundingSettings.leftSf.scbSec = 24 * 60 * 60 + 1
//            }
//        )
//        fundingTimerService.init()
//
//        assertThat(fundingTimerService.isGreenTime("leftFf")).isFalse
//        assertThat(fundingTimerService.isGreenTime("leftSf")).isTrue
//
//        println(fundingTimerService.getSecToRunLff())
//        println(fundingTimerService.getSecToRunLsf())
//    }

//    @Test
    fun testRateTask() {
        val executor = SchedulerUtils.fixedThreadExecutor("test-%d", 4)
        val startTime = LocalDateTime.now()
        val task: ScheduledFuture<*>? = executor.scheduleWithFixedDelay(
            {
                println("${passedSec(startTime)} change time in settings")
            },
            1,
            5,
            TimeUnit.SECONDS
        )

        for (i in IntStream.range(1, 15)) {
            println("${passedSec(startTime)}: ${task?.getDelay(TimeUnit.SECONDS)?.toString() ?: "-1"}")
            Thread.sleep(1000)
        }

    }

    private fun passedSec(startTime: LocalDateTime) =
        Duration.between(startTime, LocalDateTime.now()).toKotlinDuration().inWholeSeconds


    @Test
    fun shouldCalcInitDelay() {
//        whenever(arbitrageService.areBothOkex()).thenReturn(false)
//        val okCoinService = Mockito.mock(OkCoinService::class.java)
//        whenever(arbitrageService.leftMarketService).thenReturn(okCoinService)
//        whenever(arbitrageService.rightMarketService).thenReturn(okCoinService)
//        whenever(okCoinService.isSwap).thenReturn(true)
//        whenever(srs.settings).thenReturn(
//            Settings().apply {
//                fundingSettings = FundingSettings.createDefault()
//                fundingSettings.leftFf.scbSec = 0
//                fundingSettings.leftSf.scbSec = 24 * 60 * 60 + 1
//            }
//        )
//        fundingTimerService.initAtRate("leftFf")
//        val future = fundingTimerService.scheduleAtRate(
//            "leftFf",
//            LocalTime.parse("10:30"),
//            LocalTime.parse("14:00"),
//            Duration.ofSeconds(5).toMillis()
//        )
        val d = Duration.ofHours(8).toMillis()
        fundingTimerService.calcNextRunTime(LocalTime.parse("00:30"), LocalTime.parse("14:00"))
        fundingTimerService.calcNextRunTime(LocalTime.parse("01:30"), LocalTime.parse("14:00"))
        fundingTimerService.calcNextRunTime(LocalTime.parse("02:30"), LocalTime.parse("14:00"))
        fundingTimerService.calcNextRunTime(LocalTime.parse("03:30"), LocalTime.parse("14:00"))
        fundingTimerService.calcNextRunTime(LocalTime.parse("04:30"), LocalTime.parse("14:00"))
        fundingTimerService.calcNextRunTime(LocalTime.parse("05:30"), LocalTime.parse("14:00"))
        fundingTimerService.calcNextRunTime(LocalTime.parse("06:30"), LocalTime.parse("14:00"))
        fundingTimerService.calcNextRunTime(LocalTime.parse("07:30"), LocalTime.parse("14:00"))
        fundingTimerService.calcNextRunTime(LocalTime.parse("08:30"), LocalTime.parse("14:00"))
        fundingTimerService.calcNextRunTime(LocalTime.parse("09:30"), LocalTime.parse("14:00"))
        fundingTimerService.calcNextRunTime(LocalTime.parse("10:30"), LocalTime.parse("14:00"))
        fundingTimerService.calcNextRunTime(LocalTime.parse("11:30"), LocalTime.parse("14:00"))
        fundingTimerService.calcNextRunTime(LocalTime.parse("12:30"), LocalTime.parse("14:00"))
        fundingTimerService.calcNextRunTime(LocalTime.parse("13:30"), LocalTime.parse("14:00"))
        fundingTimerService.calcNextRunTime(LocalTime.parse("14:30"), LocalTime.parse("14:00"))
        fundingTimerService.calcNextRunTime(LocalTime.parse("15:30"), LocalTime.parse("14:00"))
        fundingTimerService.calcNextRunTime(LocalTime.parse("16:30"), LocalTime.parse("14:00"))
        fundingTimerService.calcNextRunTime(LocalTime.parse("17:30"), LocalTime.parse("14:00"))
        fundingTimerService.calcNextRunTime(LocalTime.parse("18:30"), LocalTime.parse("14:00"))
        fundingTimerService.calcNextRunTime(LocalTime.parse("19:30"), LocalTime.parse("14:00"))
        fundingTimerService.calcNextRunTime(LocalTime.parse("20:30"), LocalTime.parse("14:00"))
        fundingTimerService.calcNextRunTime(LocalTime.parse("21:30"), LocalTime.parse("14:00"))
        fundingTimerService.calcNextRunTime(LocalTime.parse("22:30"), LocalTime.parse("14:00"))
        fundingTimerService.calcNextRunTime(LocalTime.parse("23:30"), LocalTime.parse("14:00"))
        fundingTimerService.calcNextRunTime(LocalTime.parse("00:30"), LocalTime.parse("14:00"))
    }

}