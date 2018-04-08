package com.bitplay.persistance.domain.settings;

import com.bitplay.market.model.PlacingType;
import com.bitplay.persistance.domain.AbstractDocument;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import org.springframework.data.annotation.TypeAlias;
import org.springframework.data.mongodb.core.mapping.Document;

import java.math.BigDecimal;

import lombok.Getter;
import lombok.Setter;

/**
 * Created by Sergey Shurmin on 11/27/17.
 */
@Document(collection = "settingsCollection")
@TypeAlias("settings")
@JsonIgnoreProperties(ignoreUnknown = true)
@Getter
@Setter
public class Settings extends AbstractDocument {

    private ArbScheme arbScheme;
    private SysOverloadArgs bitmexSysOverloadArgs;
    private SysOverloadArgs okexSysOverloadArgs;
    private PlacingType bitmexPlacingType;
    private PlacingType okexPlacingType;
    private BigDecimal bitmexPrice;
    private PlacingBlocks placingBlocks;
    private Boolean restartEnabled;
    private FeeSettings feeSettings;
    private Limits limits;

    public static Settings createDefault() {
        final Settings settings = new Settings();
        settings.arbScheme = ArbScheme.SIM;
        settings.bitmexSysOverloadArgs = SysOverloadArgs.defaults();
        settings.okexSysOverloadArgs = SysOverloadArgs.defaults();
        settings.okexPlacingType = PlacingType.TAKER;
        settings.placingBlocks = PlacingBlocks.createDefault();
        settings.feeSettings = FeeSettings.createDefault();
        settings.limits = Limits.createDefault();
        settings.setId(1L);
        return settings;
    }

    public BigDecimal getBFee() {
        final FeeSettings feeSettings = getFeeSettings();
        final PlacingType bitmexPlacingType = getBitmexPlacingType();
        if (bitmexPlacingType == PlacingType.MAKER) {
            return feeSettings.getbMakerComRate();
        }
        return feeSettings.getbTakerComRate();
    }

    public BigDecimal getOFee() {
        final FeeSettings feeSettings = getFeeSettings();
        final PlacingType okexPlacingType = getOkexPlacingType();
        if (okexPlacingType == PlacingType.MAKER) {
            return feeSettings.getoMakerComRate();
        }
        return feeSettings.getoTakerComRate(); // TAKER, HYBRID
    }

    @Override
    public String toString() {
        return "Settings{" +
                "arbScheme=" + arbScheme +
                ", bitmexSysOverloadArgs=" + bitmexSysOverloadArgs +
                ", okexSysOverloadArgs=" + okexSysOverloadArgs +
                ", okexPlacingType=" + okexPlacingType +
                ", bitmexPrice=" + bitmexPrice +
                ", placingBlocks=" + placingBlocks +
                ", restartEnabled=" + restartEnabled +
                ", feeSettings=" + feeSettings +
                '}';
    }
}
