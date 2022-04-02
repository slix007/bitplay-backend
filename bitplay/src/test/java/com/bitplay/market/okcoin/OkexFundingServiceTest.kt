package com.bitplay.market.okcoin

import com.bitplay.arbitrage.ArbitrageService
import com.bitplay.arbitrage.FundingTimerService
import com.bitplay.persistance.SettingsRepositoryService
import org.mockito.Mockito
import org.springframework.context.ApplicationEvent
import org.springframework.context.ApplicationEventPublisher
import java.time.LocalTime
import java.time.format.DateTimeFormatter

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
    private val fundingTimerService: FundingTimerService = FundingTimerService(applicationEventPublisher, srs, arbitrageService)

    //    @Test
    fun nextRun() {
        val now = LocalTime.now()
        val formatter = DateTimeFormatter.ofPattern("HH:mm:ss");
        println(now.format(formatter))
        fundingTimerService.scheduleNextRun(
            LocalTime.parse("13:36:00.897"),
            ""
        )
//        Mockito.verify(srs.updateFundingSettings())
    }
}