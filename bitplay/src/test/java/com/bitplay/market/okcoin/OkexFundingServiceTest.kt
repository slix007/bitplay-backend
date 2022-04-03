package com.bitplay.market.okcoin

import com.bitplay.arbitrage.ArbitrageService
import com.bitplay.arbitrage.FundingTimerService
import com.bitplay.persistance.SettingsRepositoryService
import com.bitplay.persistance.domain.settings.FundingSettings
import com.bitplay.persistance.domain.settings.Settings
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import org.mockito.Mockito
import org.mockito.kotlin.whenever
import org.springframework.context.ApplicationEvent
import org.springframework.context.ApplicationEventPublisher
import java.time.Duration
import java.time.LocalTime
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

    @Test
    fun nextRun() {
        whenever(arbitrageService.areBothOkex()).thenReturn(false)
        whenever(srs.settings).thenReturn(
            Settings().apply {
                fundingSettings = FundingSettings.createDefault()
                fundingSettings.leftFf.scbSec = 0
                fundingSettings.leftSf.scbSec = 24 * 60 * 60 + 1
            }
        )
        fundingTimerService.init()

        assertThat(fundingTimerService.isGreenTime("leftFf")).isFalse
        assertThat(fundingTimerService.isGreenTime("leftSf")).isTrue

        println(fundingTimerService.getSecToRunLff())
        println(fundingTimerService.getSecToRunLsf())
    }

    @Test
    fun betweenTest() {
        testOneTime("14:16")
        testOneTime("20:16")
        testOneTime("04:16")
    }

    private fun testOneTime(time: String) {
        var b = Duration.between(LocalTime.parse(time), LocalTime.parse("20:00:00")).toKotlinDuration()
        println(
            "$time to 20:00:00=$b, \tf=${fundingTimerService.isFirstByHours(b)}, \ts= ${
                fundingTimerService.isSecondByHours(
                    b
                )
            }"
        )
        b = Duration.between(LocalTime.parse(time), LocalTime.parse("04:00:00")).toKotlinDuration()
        println(
            "$time to 04:00:00=$b, \tf=${fundingTimerService.isFirstByHours(b)}, \ts= ${
                fundingTimerService.isSecondByHours(
                    b
                )
            }"
        )
        b = Duration.between(LocalTime.parse(time), LocalTime.parse("12:00:00")).toKotlinDuration()
        println(
            "$time to 12:00:00=$b, \tf=${fundingTimerService.isFirstByHours(b)}, \ts= ${
                fundingTimerService.isSecondByHours(
                    b
                )
            }"
        )
    }
}