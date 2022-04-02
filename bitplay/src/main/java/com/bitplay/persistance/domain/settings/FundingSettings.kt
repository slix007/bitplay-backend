package com.bitplay.persistance.domain.settings

import java.time.LocalTime


data class FundingSettings(
    val leftFf: FundingS,
    val leftSf: FundingS,
    val rightFf: FundingS,
    val rightSf: FundingS,
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
        var time: String,
        /** start calculate before */
        var scbSec: Long,
    ) {
        fun getFundingTime(): LocalTime = LocalTime.parse(time)
//        fun setFundingTime(time: String) {
//            if (LocalTime.parse(time) != null) {
//                this.time = time
//            }
//        }
    }

    fun getByParamName(paramName: String): FundingS =
        when (paramName) {
            "leftFf" -> leftFf
            "leftSf" -> leftSf
            "rightFf" -> rightFf
            "rightSf" -> rightSf
            else -> throw IllegalArgumentException("Illegal funding paramName: $paramName")
        }

    fun update(update: FundingSettingsUpdate) =
        this.getByParamName(update.paramName!!).apply {
            if (update.time != null) {
                try {
                    println(LocalTime.parse(update.time))
                    time = update.time!!
                } catch (e: Exception) {
                    println("can not parse time $update")
                }
            }
            scbSec = update.scbSec ?: scbSec
        }
}

/**
 * Need default constructor for Jackson => all fields are null by default
 */
data class FundingSettingsUpdate(
    var paramName: String? = null,
    var time: String? = null,
    var scbSec: Long? = null
)

