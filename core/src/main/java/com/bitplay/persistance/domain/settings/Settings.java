package com.bitplay.persistance.domain.settings;

import com.bitplay.market.model.PlacingType;
import com.bitplay.persistance.domain.AbstractDocument;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import org.springframework.data.annotation.TypeAlias;
import org.springframework.data.mongodb.core.mapping.Document;

import java.math.BigDecimal;

/**
 * Created by Sergey Shurmin on 11/27/17.
 */
@Document(collection = "settingsCollection")
@TypeAlias("settings")
@JsonIgnoreProperties(ignoreUnknown = true)
public class Settings extends AbstractDocument {

    private ArbScheme arbScheme;
    private SysOverloadArgs bitmexSysOverloadArgs;
    //    private SysOverloadArgs okexSysOverloadArgs;
    private PlacingType okexPlacingType;
    private BigDecimal bitmexPrice;

    public static Settings createDefault() {
        final Settings settings = new Settings();
        settings.arbScheme = ArbScheme.MT;
        settings.bitmexSysOverloadArgs = SysOverloadArgs.defaults();
//        settings.okexSysOverloadArgs = SysOverloadArgs.defaults();
        settings.okexPlacingType = PlacingType.TAKER;
        settings.setId(1L);
        return settings;
    }

    public ArbScheme getArbScheme() {
        return arbScheme;
    }

    public void setArbScheme(ArbScheme arbScheme) {
        this.arbScheme = arbScheme;
    }

    public SysOverloadArgs getBitmexSysOverloadArgs() {
        return bitmexSysOverloadArgs;
    }

    public void setBitmexSysOverloadArgs(SysOverloadArgs bimexSysOverloadArgs) {
        this.bitmexSysOverloadArgs = bimexSysOverloadArgs;
    }

    public PlacingType getOkexPlacingType() {
        if (okexPlacingType == null) {
            okexPlacingType = PlacingType.TAKER;
        }
        return okexPlacingType;
    }

    public void setOkexPlacingType(PlacingType okexPlacingType) {
        this.okexPlacingType = okexPlacingType;
    }

    public BigDecimal getBitmexPrice() {
        if (bitmexPrice == null) {
            bitmexPrice = BigDecimal.ZERO;
        }
        return bitmexPrice;
    }

    public void setBitmexPrice(BigDecimal bitmexPrice) {
        this.bitmexPrice = bitmexPrice;
    }

    @Override
    public String toString() {
        return "Settings{" +
                "arbScheme=" + arbScheme +
                ", bitmexSysOverloadArgs=" + bitmexSysOverloadArgs +
                ", okexPlacingType=" + okexPlacingType +
                '}';
    }
}
