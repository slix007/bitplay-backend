package com.bitplay.arbitrage

import com.bitplay.arbitrage.events.ArbitrageReadyEnableFundingEvent
import com.bitplay.persistance.SettingsRepositoryService
import com.bitplay.utils.SchedulerUtils
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Service
import java.time.Duration
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import kotlin.time.DurationUnit
import kotlin.time.toKotlinDuration

@Service
class FundingTimerService(
    val applicationEventPublisher: ApplicationEventPublisher,
    val settingsRepositoryService: SettingsRepositoryService,
    val arbitrageService: ArbitrageService,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    private val executor: ScheduledExecutorService =
        SchedulerUtils.fixedThreadExecutor("okex-funding-check-%d", 4)

//    private val exLff = SchedulerUtils.singleThreadExecutor("exLff-%d")
//    private val exLsf = SchedulerUtils.singleThreadExecutor("exLsf-%d")
//    private val exRff = SchedulerUtils.singleThreadExecutor("exRff-%d")
//    private val exRsf = SchedulerUtils.singleThreadExecutor("exRsf-%d")

    @Volatile
    private var futureLff: ScheduledFuture<*>? = null

    @Volatile
    private var futureLsf: ScheduledFuture<*>? = null

    @Volatile
    private var futureRff: ScheduledFuture<*>? = null

    @Volatile
    private var futureRsf: ScheduledFuture<*>? = null


    @EventListener(ArbitrageReadyEnableFundingEvent::class)
    fun init() {
        if (!arbitrageService.areBothOkex()) {
            scheduleNextRunToFuture("leftFf")
            scheduleNextRunToFuture("leftSf")
            scheduleNextRunToFuture("rightFf")
            scheduleNextRunToFuture("rightSf")
        }
    }

    private fun scheduleNextRunToFuture(paramName: String) {
        val future = scheduleNextRun(LocalTime.now(), paramName)
        when (paramName) {
            "leftFf" -> futureLff = future
            "leftSf" -> futureLsf = future
            "rightFf" -> futureRff = future
            "rightSf" -> futureRsf = future
        }
    }

    fun scheduleNextRun(currentTime: LocalTime, paramName: String): ScheduledFuture<*> {
        val nextRunTime: LocalTime = settingsRepositoryService.settings
            .fundingSettings.getByParamName(paramName).getFundingTime()

        var between = Duration.between(currentTime, nextRunTime).toKotlinDuration()
        var timeTmp = nextRunTime
        while ((between.isNegative() && between.absoluteValue.toDouble(DurationUnit.HOURS) < 16)
            || between.toDouble(DurationUnit.HOURS) > 8
        ) {
            timeTmp = timeTmp.plus(8, ChronoUnit.HOURS)
            between = Duration.between(currentTime, timeTmp).toKotlinDuration()
        }
        if (nextRunTime != timeTmp) {
            settingsRepositoryService.updateFundingTime(
                paramName,
                timeTmp.format(DateTimeFormatter.ofPattern("HH:mm:ss"))
            )
            logger.info("time=$nextRunTime to timeTmp=$timeTmp")
        }
        logger.info("schedule nextRun at $nextRunTime to timeTmp=$timeTmp")

        return executor.schedule({ nextRun(paramName) }, between.inWholeMilliseconds, TimeUnit.MILLISECONDS)
    }

    fun nextRun(paramName: String) {
        logger.info("FundingService run for $paramName")

        //TODO start the funding


        scheduleNextRunToFuture(paramName)
    }

    fun isGreenTimeAll(): Boolean {
        val fundingSettings = settingsRepositoryService.settings.fundingSettings
        return isGreenTime(futureLff, fundingSettings.leftFf.scbSec)
                && isGreenTime(futureLsf, fundingSettings.leftSf.scbSec)
                && isGreenTime(futureRff, fundingSettings.rightFf.scbSec)
                && isGreenTime(futureRsf, fundingSettings.rightSf.scbSec)
    }

    fun isGreenTime(paramName: String): Boolean {
        val fundingSettings = settingsRepositoryService.settings.fundingSettings
        return when (paramName) {
            "leftFf" -> isGreenTime(futureLff, fundingSettings.leftFf.scbSec)
            "leftSf" -> isGreenTime(futureLsf, fundingSettings.leftSf.scbSec)
            "rightFf" -> isGreenTime(futureRff, fundingSettings.rightFf.scbSec)
            "rightSf" -> isGreenTime(futureRsf, fundingSettings.rightSf.scbSec)
            else -> false
        }
    }

    private fun isGreenTime(future: ScheduledFuture<*>?, startCalculateBeforeSec: Long): Boolean {
        val delayToStartSec: Long? = future?.getDelay(TimeUnit.SECONDS)
        return delayToStartSec != null && delayToStartSec < startCalculateBeforeSec
    }

    fun getSecToRunLff(): String = futureLff?.getDelay(TimeUnit.SECONDS)?.toString() ?: "-1"
    fun getSecToRunLsf(): String = futureLsf?.getDelay(TimeUnit.SECONDS)?.toString() ?: "-1"
    fun getSecToRunRff(): String = futureRff?.getDelay(TimeUnit.SECONDS)?.toString() ?: "-1"
    fun getSecToRunRsf(): String = futureRsf?.getDelay(TimeUnit.SECONDS)?.toString() ?: "-1"

}