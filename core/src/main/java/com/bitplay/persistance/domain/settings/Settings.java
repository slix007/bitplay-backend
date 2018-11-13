package com.bitplay.persistance.domain.settings;

import com.bitplay.market.model.PlacingType;
import com.bitplay.persistance.domain.AbstractDocument;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.springframework.data.annotation.Transient;
import org.springframework.data.annotation.TypeAlias;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Created by Sergey Shurmin on 11/27/17.
 */
@Document(collection = "settingsCollection")
@TypeAlias("settings")
@JsonIgnoreProperties(ignoreUnknown = true)
@Getter
@Setter
@ToString
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder=true)
public class Settings extends AbstractDocument {

    private ArbScheme arbScheme;
    private SysOverloadArgs bitmexSysOverloadArgs;
    private SysOverloadArgs okexSysOverloadArgs;
    private PlacingType bitmexPlacingType;
    private PlacingType okexPlacingType;
    private BigDecimal bitmexPrice;
    private PlacingBlocks placingBlocks;
    private PosAdjustment posAdjustment;
    private Boolean restartEnabled;
    private FeeSettings feeSettings;
    private Limits limits;
    private RestartSettings restartSettings;
    private Integer signalDelayMs;
    private BigDecimal coldStorageBtc;
    private Integer eBestMin;
    private UsdQuoteType usdQuoteType;
    private BigDecimal okexFakeTakerDev;//deviation of fake taker price
    private Boolean adjustByNtUsd;

    @Deprecated
    /* Use {@link #contractMode}. */
    private OkexContractType okexContractType;
    @Deprecated
    /* Use {@link #contractMode}. */
    private BitmexContractType bitmexContractType;
    private ContractMode contractMode;
    @Transient
    private String okexContractName;
    @Transient
    private ContractMode contractModeCurrent; // only for UI
    @Transient
    private String mainSetNameCurrent;
    @Transient
    private String extraSetNameCurrent;

    private BigDecimal hedgeBtc;
    private BigDecimal hedgeEth;
    private Boolean hedgeAuto;

    public static Settings createDefault() {
        final Settings settings = new Settings();
        settings.arbScheme = ArbScheme.SIM;
        settings.bitmexSysOverloadArgs = SysOverloadArgs.defaults();
        settings.okexSysOverloadArgs = SysOverloadArgs.defaults();
        settings.okexPlacingType = PlacingType.TAKER;
        settings.placingBlocks = PlacingBlocks.createDefault();
        settings.posAdjustment = PosAdjustment.createDefault();
        settings.feeSettings = FeeSettings.createDefault();
        settings.limits = Limits.createDefault();
        settings.restartSettings = RestartSettings.createDefaults();
        settings.signalDelayMs = 1000;
        settings.contractMode = ContractMode.MODE1_SET_BU11;
        settings.okexContractType = OkexContractType.BTC_ThisWeek;
        settings.bitmexContractType = BitmexContractType.XBTUSD;
        settings.coldStorageBtc = BigDecimal.ZERO;
        settings.eBestMin = 0;
        settings.hedgeBtc = BigDecimal.ZERO;
        settings.hedgeEth = BigDecimal.ZERO;
        settings.hedgeAuto = false;
        settings.okexFakeTakerDev = BigDecimal.ONE;
        settings.setId(1L);
        return settings;
    }

    public BigDecimal getBFee(PlacingType placingType) {
        final FeeSettings feeSettings = getFeeSettings();
        if (placingType == PlacingType.MAKER || placingType == PlacingType.MAKER_TICK) {
            return feeSettings.getbMakerComRate();
        }
        return feeSettings.getbTakerComRate();
    }

    public BigDecimal getOFee(PlacingType placingType) {
        final FeeSettings feeSettings = getFeeSettings();
        if (placingType == PlacingType.MAKER || placingType == PlacingType.MAKER_TICK) {
            return feeSettings.getoMakerComRate();
        }
        return feeSettings.getoTakerComRate(); // TAKER, HYBRID
    }

    // only for UI
    public boolean isEth() {
        return contractModeCurrent != null && contractModeCurrent.isEth();
    }

    public void setContractModeCurrent(ContractMode contractModeCurrent) {
        this.contractModeCurrent = contractModeCurrent;
        this.mainSetNameCurrent = contractModeCurrent.getMainSetName();
        this.extraSetNameCurrent = contractModeCurrent.getExtraSetName();
    }
}
