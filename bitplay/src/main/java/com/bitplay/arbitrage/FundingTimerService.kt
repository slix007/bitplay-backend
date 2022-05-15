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

    private var executor: ScheduledExecutorService =
        SchedulerUtils.fixedThreadExecutor("okex-funding-check-%d", 4)

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
        executor = SchedulerUtils.fixedThreadExecutor("okex-funding-check-%d", 4)
        if (arbitrageService.leftMarketService.isSwap) {
            scheduleNextRunToFuture("leftFf", 8)
            scheduleNextRunToFuture("leftSf", 8)
        }
        if (arbitrageService.rightMarketService.isSwap) {
            scheduleNextRunToFuture("rightFf", 8)
            scheduleNextRunToFuture("rightSf", 8)
        }
    }

    private fun scheduleNextRunToFuture(paramName: String, hoursForward: Int) {
        val future = scheduleNextRun(paramName, hoursForward)
        when (paramName) {
            "leftFf" -> futureLff = future
            "leftSf" -> futureLsf = future
            "rightFf" -> futureRff = future
            "rightSf" -> futureRsf = future
        }
    }

    private fun scheduleNextRun(paramName: String, hoursForward: Int): ScheduledFuture<*> {
        val nextRunTime: LocalTime = settingsRepositoryService.settings
            .fundingSettings.getByParamName(paramName).getFundingTimeReal()

        var between = Duration.between(LocalTime.now(), nextRunTime).toKotlinDuration()
        if (between.isNegative()) {
            between = between.plus(Duration.ofHours(24L).toKotlinDuration())
        }
        var timeTmp = nextRunTime
        var iterations = 0;
        val isFirst = hoursForward == 8
        while (((isFirst && !isFirstByHours(between)) || (!isFirst && !isSecondByHours(between)))
            && iterations++ < 5 // loop defence
        ) {
            timeTmp = timeTmp.plus(8, ChronoUnit.HOURS)
            between = Duration.between(LocalTime.now(), timeTmp).toKotlinDuration()
            if (between.isNegative()) {
                between = between.plus(Duration.ofHours(24L).toKotlinDuration())
            }
            logger.info("time=$nextRunTime to timeTmp=$timeTmp")
        }
        if (nextRunTime != timeTmp) {
            settingsRepositoryService.updateFundingTime(
                paramName,
                timeTmp.format(DateTimeFormatter.ofPattern("HH:mm:ss"))
            )
        }
        logger.info("schedule nextRun at $nextRunTime to timeTmp=$timeTmp")

        between = Duration.between(LocalTime.now(), timeTmp).toKotlinDuration()
        if (between.isNegative()) {
            between = between.plus(Duration.ofHours(24L).toKotlinDuration())
        }
        return executor.schedule(
            { nextRun(paramName, hoursForward) },
            between.inWholeMilliseconds,
            TimeUnit.MILLISECONDS
        )
    }

    // 0..8..16..24
    // 0..8........
    fun isFirstByHours(between: kotlin.time.Duration) =
        (between.isNegative()
                && between.absoluteValue.toDouble(DurationUnit.HOURS) > 16
                )
                ||
                (between.isPositive() && between.toDouble(DurationUnit.HOURS) < 8)

    // 0..8..16..24
    // ...8..16....
    fun isSecondByHours(between: kotlin.time.Duration) =
        (between.isNegative()
                && between.absoluteValue.toDouble(DurationUnit.HOURS) < 16
                && between.absoluteValue.toDouble(DurationUnit.HOURS) > 8
                )
                || (
                between.isPositive()
                        && between.absoluteValue.toDouble(DurationUnit.HOURS) > 8
                        && between.absoluteValue.toDouble(DurationUnit.HOURS) < 16
                )

    fun nextRun(paramName: String, hoursForward: Int) {
        logger.info("FundingService run for $paramName")

        //TODO start the funding


        scheduleNextRunToFuture(paramName, hoursForward)
    }

    fun noOneGreen(): Boolean {
        val fundingSettings = settingsRepositoryService.settings.fundingSettings
        return !isGreenTime(getSecToRunLff(), fundingSettings.leftFf.scbSec)
                && !isGreenTime(getSecToRunLsf(), fundingSettings.leftSf.scbSec)
                && !isGreenTime(getSecToRunRff(), fundingSettings.rightFf.scbSec)
                && !isGreenTime(getSecToRunRsf(), fundingSettings.rightSf.scbSec)
    }

    fun isGreenTime(paramName: String): Boolean {
        val fundingSettings = settingsRepositoryService.settings.fundingSettings
        return when (paramName) {
            "leftFf" -> isGreenTime(getSecToRunLff(), fundingSettings.leftFf.scbSec)
            "leftSf" -> isGreenTime(getSecToRunLsf(), fundingSettings.leftSf.scbSec)
            "rightFf" -> isGreenTime(getSecToRunRff(), fundingSettings.rightFf.scbSec)
            "rightSf" -> isGreenTime(getSecToRunRsf(), fundingSettings.rightSf.scbSec)
            else -> false
        }
    }

    private val HOURS_8_IN_SEC = 60 * 60 * 8

    private fun isGreenTime(
        delayToStartSecStr: String,
        startCalculateBeforeSec: Long,
    ): Boolean {
        val delayToStartSec: Long = delayToStartSecStr.toLong()
        return delayToStartSec != -1L && delayToStartSec < startCalculateBeforeSec
    }

    fun getSecToRunLff(): String = futureLff?.getDelay(TimeUnit.SECONDS)?.toString() ?: "-1"
    fun getSecToRunLsf(): String =
        futureLsf?.getDelay(TimeUnit.SECONDS)?.let { it + HOURS_8_IN_SEC }?.toString() ?: "-1"

    fun getSecToRunRff(): String = futureRff?.getDelay(TimeUnit.SECONDS)?.toString() ?: "-1"
    fun getSecToRunRsf(): String =
        futureRsf?.getDelay(TimeUnit.SECONDS)?.let { it + HOURS_8_IN_SEC }?.toString() ?: "-1"

    fun reschedule() {
        executor.shutdown()
        init()
    }

    fun isLeftActive() = futureLff != null && futureLsf != null
    fun isRightActive() = futureRff != null && futureRsf != null

}