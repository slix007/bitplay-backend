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
import kotlin.time.toKotlinDuration

@Service
class FundingTimerService(
    val applicationEventPublisher: ApplicationEventPublisher,
    val settingsRepositoryService: SettingsRepositoryService,
    val arbitrageService: ArbitrageService,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    private var executor: ScheduledExecutorService =
        SchedulerUtils.fixedThreadExecutor("funding-check-%d", 4)

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
        executor = SchedulerUtils.fixedThreadExecutor("funding-check-%d", 4)
        if (arbitrageService.leftMarketService.isSwap) {
            initAtRate("leftFf")
            initAtRate("leftSf")
        }
        if (arbitrageService.rightMarketService.isSwap) {
            initAtRate("rightFf")
            initAtRate("rightSf")
        }
    }

    private fun initAtRate(paramName: String) {
        try {
            val future = scheduleAtRate(
                paramName,
                LocalTime.now(),
                settingsRepositoryService.settings.fundingSettings.getByParamName(paramName).getFundingTimeReal(),
                Duration.ofHours(8).toMillis()
            )
            when (paramName) {
                "leftFf" -> futureLff = future
                "leftSf" -> futureLsf = future
                "rightFf" -> futureRff = future
                "rightSf" -> futureRsf = future
            }
        } catch (e: Exception) {
            logger.error("Can not scheduleNextRun for $paramName")
        }
    }

    fun scheduleAtRate(
        paramName: String,
        currentTime: LocalTime,
        settingsTime: LocalTime,
        durationMillis: Long
    ): ScheduledFuture<*> {
        val (between, timeTmp) = calcNextRunTime(settingsTime, currentTime)
        logger.info("Schedule $paramName initDelay:$between, settingsTime=$settingsTime, timeTmp=$timeTmp")
        return executor.scheduleWithFixedDelay(
            {
                // change time on UI and in settings
                logger.info("FundingService run for $paramName do nothing")
            },
            between.inWholeMilliseconds,
            durationMillis,
            TimeUnit.MILLISECONDS
        )
    }

    fun calcNextRunTime(
        settingsTime: LocalTime,
        currentTime: LocalTime
    ): Pair<kotlin.time.Duration,LocalTime> {
        var timeTmp = settingsTime
        var between = Duration.between(currentTime, timeTmp).toKotlinDuration()
        if (between.isNegative()) {
            between = between.plus(Duration.ofHours(24L).toKotlinDuration())
        }

        var ind = 0;
        while (
            between.inWholeMilliseconds > Duration.ofHours(8).toKotlinDuration().inWholeMilliseconds
            && ind++ < 5
        ) {
            timeTmp = timeTmp.plus(8, ChronoUnit.HOURS)
            between = Duration.between(currentTime, timeTmp).toKotlinDuration()
            if (between.isNegative()) {
                between = between.plus(Duration.ofHours(24L).toKotlinDuration())
            }
    //            logger.info("$ind: settings=$settingsTime to initTime=$timeTmp for $paramName. Delay=${between}")
            if (ind > 3) {
                logger.warn("$ind: initDelay:$between, settingsTime=$settingsTime, timeTmp=$timeTmp ")
            }
        }
        return between to timeTmp
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

    fun getFundingTimeUi(paramName: String, isSecond: Boolean): String {
        val byParamName = settingsRepositoryService.settings.fundingSettings.getByParamName(paramName)
        val settingsTime = byParamName.getFundingTimeReal()
        val (_, settingsTimeTmp) = calcNextRunTime(settingsTime, LocalTime.now())
        val fmt: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss")
        return byParamName
            .getFundingTimeUi(isSecond, settingsTimeTmp.format(fmt))
    }

    fun reschedule() {
        executor.shutdown()
        init()
    }

    fun isLeftActive() = futureLff != null && futureLsf != null
    fun isRightActive() = futureRff != null && futureRsf != null

}