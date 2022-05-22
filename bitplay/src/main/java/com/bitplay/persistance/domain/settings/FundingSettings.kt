package com.bitplay.persistance.domain.settings

import java.time.LocalTime
import java.time.format.DateTimeFormatter


data class FundingSettings(
    val leftFf: FundingS,
    val leftSf: FundingS,
    val rightFf: FundingS,
    val rightSf: FundingS,
    var fundingResultEnabled: Boolean = false,
) {
    companion object {
        @JvmStatic
        fun createDefault(): FundingSettings =
            FundingSettings(
                FundingS("20:00:00", 0),
                FundingS("20:00:00", 0),
                FundingS("20:00:00", 0),
                FundingS("20:00:00", 0),
            )
    }

    data class FundingS(
        private var time: String,
        /** start calculate before */
        var scbSec: Long,
    ) {
        fun getFundingTimeReal(): LocalTime = LocalTime.parse(time)
        fun getFundingTimeUi(isSecond: Boolean = false): String =
            getFundingTimeUi(isSecond, time)

        fun getFundingTimeUi(isSecond: Boolean = false, theTime: String): String =
            if (isSecond) {
                val fmt: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss")
                fmt.format(LocalTime.parse(theTime).plusHours(8))
            } else {
                val fmt: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss")
                fmt.format(LocalTime.parse(theTime))
            }

        fun setFundingTimeUi(time: String, isSecond: Boolean = false) {
            val parsed = LocalTime.parse(time)
            if (parsed != null) {
                if (isSecond) {
                    val fmt: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss")
                    this.time = fmt.format(parsed.plusHours(8))
                } else {
                    val fmt: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss")
                    this.time = fmt.format(parsed)
                }
            }
        }
    }

    fun getByParamName(paramName: String): FundingS =
        when (paramName) {
            "leftFf" -> leftFf
            "leftSf" -> leftSf
            "rightFf" -> rightFf
            "rightSf" -> rightSf
            else -> throw IllegalArgumentException("Illegal funding paramName: $paramName")
        }

    fun update(update: FundingSettingsUpdate) {
        if (update.paramName != null) {
            this.getByParamName(update.paramName!!).apply {
                if (update.time != null) {
                    try {
                        setFundingTimeUi(
                            update.time!!,
                            update.paramName!! == "leftSf" || update.paramName!! == "rightSf"
                        )
                    } catch (e: Exception) {
                        println("can not parse time $update")
                    }
                }
                update.scbString?.let { scbStr: String ->
                    try {
                        val parsed = LocalTime.parse(scbStr)
                        if (parsed != null) {
                            scbSec = parsed.toSecondOfDay().toLong()
                        }
                    } catch (e: Exception) {
                        println("can not parse scbString $update")
                    }
                }
            }
        } else {
            update.fundingResultEnabled?.let {
                fundingResultEnabled = it
            }
        }
    }
}

/**
 * Need default constructor for Jackson => all fields are null by default
 */
data class FundingSettingsUpdate(
    // paramName or fundingResultEnabled not null
    var fundingResultEnabled: Boolean? = null,
    var paramName: String? = null,
    var time: String? = null,
    var scbSec: Long? = null, //not in use
    var scbString: String? = null,
)

