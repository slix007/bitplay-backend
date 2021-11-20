package com.bitplay.persistance.domain.settings;

import com.bitplay.market.okcoin.OkCoinService;
import com.bitplay.persistance.domain.AbstractDocument;
import com.bitplay.persistance.domain.correction.CorrParams;
import com.bitplay.persistance.domain.fluent.TradingModeState;
import com.bitplay.persistance.domain.settings.SettingsVolatileMode.Field;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.math.BigDecimal;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
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
@Builder(toBuilder = true)
public class Settings extends AbstractDocument {

    private ManageType manageType;
    private EnumSet<ExtraFlag> extraFlags;
    private ArbScheme arbScheme;
    private AbortSignal abortSignal; // only ArbScheme.R_wait_L_portions
    private SysOverloadArgs bitmexSysOverloadArgs;
    private SysOverloadArgs okexSysOverloadArgs;
    private AllPostOnlyArgs allPostOnlyArgs;
    private PlacingType leftPlacingType;
    private PlacingType rightPlacingType;
    private BigDecimal bitmexPrice;
    private PlacingBlocks placingBlocks;
    private PosAdjustment posAdjustment;
    private Boolean restartEnabled;
    private FeeSettings feeSettings;
    private Limits limits;
    private RestartSettings restartSettings;
    private Integer signalDelayMs;
    private BigDecimal coldStorageBtc;
    private BigDecimal coldStorageEth;
    private Integer eBestMin;
    private UsdQuoteType usdQuoteType;
    private Implied implied;
    private AllFtpd allFtpd;
    private Boolean adjustByNtUsd;
    private BigDecimal ntUsdMultiplicityOkexLeft;
    private BigDecimal ntUsdMultiplicityOkex;
    private AmountType amountTypeBitmex;
    private AmountType amountTypeOkex;
    private BigDecimal bitmexFokMaxDiff; // bitmex fillOrKill max diff price
    private Boolean bitmexFokMaxDiffAuto;
    private BigDecimal bitmexFokTotalDiff;
    private BitmexObType bitmexObType;
    @Transient
    private BitmexObType bitmexObTypeCurrent; // only for UI

    //    private ContractMode contractMode;
    private ContractMode contractMode;
    @Transient
    private ContractMode contractModeCurrent; // only for UI
    @Transient
    private Map<String, String> okexContractNames = new HashMap<>(); // only for UI. enumName to contractNameWithExpDate
    @Transient
    private Map<String, String> bitmexContractNames = new HashMap<>(); // only for UI. enumName to contractNameWithTYpe

    private BigDecimal hedgeBtc;
    private BigDecimal hedgeEth;
    private Boolean hedgeAuto;

    // Trading modes: Current/Volatile
    private Boolean tradingModeAuto;
    private SettingsVolatileMode settingsVolatileMode;
    private TradingModeState tradingModeState;

    // Bitmex change on SO
    private BitmexChangeOnSo bitmexChangeOnSo;

    private Boolean okexEbestElast;

    @Transient
    private CorrParams corrParams;

    private String currentPreset;

    private Dql dql;
    private Boolean preSignalObReFetch;
    @Transient
    private final SettingsTransient settingsTransient = new SettingsTransient();

    private OkexSettlement okexSettlement;

    private ConBoPortions conBoPortions;

    private BitmexCtList bitmexContractTypes;
    @Transient
    private boolean restartWarnBitmexCt = false;

    private SettingsTimestamps settingsTimestamps;
    private BtmAvgPriceUpdateSettings btmAvgPriceUpdateSettings;

    public static Settings createDefault() {
        final Settings settings = new Settings();
        settings.manageType = ManageType.AUTO;
        settings.arbScheme = ArbScheme.L_with_R;
        settings.bitmexSysOverloadArgs = SysOverloadArgs.defaults();
        settings.okexSysOverloadArgs = SysOverloadArgs.defaults();
        settings.allPostOnlyArgs = AllPostOnlyArgs.defaults();
        settings.rightPlacingType = PlacingType.TAKER;
        settings.placingBlocks = PlacingBlocks.createDefault();
        settings.posAdjustment = PosAdjustment.createDefault();
        settings.feeSettings = FeeSettings.createDefault();
        settings.btmAvgPriceUpdateSettings = BtmAvgPriceUpdateSettings.createDefault();
        settings.limits = Limits.createDefault();
        settings.restartSettings = RestartSettings.createDefaults();
        settings.signalDelayMs = 1000;
        settings.contractMode = new ContractMode(BitmexContractType.XBTUSD_Perpetual, OkexContractType.BTC_ThisWeek);
        settings.coldStorageBtc = BigDecimal.ZERO;
        settings.coldStorageEth = BigDecimal.ZERO;
        settings.eBestMin = 0;
        settings.hedgeBtc = BigDecimal.ZERO;
        settings.hedgeEth = BigDecimal.ZERO;
        settings.hedgeAuto = false;
        settings.usdQuoteType = UsdQuoteType.INDEX_LEFT;
        settings.implied = Implied.createDefaults();
        settings.allFtpd = AllFtpd.createDefaults();
        settings.tradingModeAuto = false;
        settings.tradingModeState = new TradingModeState(TradingMode.CURRENT);
        settings.bitmexChangeOnSo = new BitmexChangeOnSo();
        settings.okexEbestElast = false;
        settings.dql = new Dql();
        settings.dql.setDqlLevel(BigDecimal.ZERO);
        settings.bitmexObType = BitmexObType.INCREMENTAL_25;
        settings.okexSettlement = OkexSettlement.createDefault();
        settings.conBoPortions = ConBoPortions.createDefault();
        settings.bitmexContractTypes = BitmexCtList.createDefault();
        settings.settingsTimestamps = SettingsTimestamps.createDefault();
        settings.setId(1L);
        return settings;
    }

    public Dql getDql() {
        if (contractMode != null
                && contractMode.getLeft() != null
                && contractMode.getLeft().getMarketName() != null
                && contractMode.getLeft().getMarketName().equals(OkCoinService.NAME)
                && contractMode.getRight() != null
                && contractMode.getRight().getMarketName() != null
                && contractMode.getRight().getMarketName().equals(OkCoinService.NAME)
        ) {
            dql.setLeftDqlOpenMin(dql.getRightDqlOpenMin());
            dql.setLeftDqlCloseMin(dql.getRightDqlCloseMin());
            dql.setLeftDqlKillPos(dql.getRightDqlKillPos());
        }
        return dql;
    }

    public BigDecimal getLeftFee(PlacingType placingType) {
        final FeeSettings feeSettings = getFeeSettings();
        if (placingType == PlacingType.MAKER || placingType == PlacingType.MAKER_TICK) {
            return feeSettings.getLeftMakerComRate();
        }
        return feeSettings.getLeftTakerComRate();
    }

    public BigDecimal getOFee(PlacingType placingType) {
        final FeeSettings feeSettings = getFeeSettings();
        if (placingType == PlacingType.MAKER || placingType == PlacingType.MAKER_TICK) {
            return feeSettings.getRightMakerComRate();
        }
        return feeSettings.getRightTakerComRate(); // TAKER, HYBRID
    }

    // only for UI
    public boolean isEth() {
        return contractModeCurrent != null && contractModeCurrent.isEth();
    }

    // Volatile mode
    public PlacingType getLeftPlacingType() {
        return tradingModeState != null && settingsVolatileMode != null
                && tradingModeState.getTradingMode() == TradingMode.VOLATILE
                && settingsVolatileMode.getActiveFields().contains(Field.leftPlacingType)
                ? settingsVolatileMode.getLeftPlacingType()
                : leftPlacingType;
    }

    public PlacingType getRightPlacingType() {
        return tradingModeState != null && settingsVolatileMode != null
                && tradingModeState.getTradingMode() == TradingMode.VOLATILE
                && settingsVolatileMode.getActiveFields().contains(Field.rightPlacingType)
                ? settingsVolatileMode.getRightPlacingType()
                : rightPlacingType;
    }

    public Integer getSignalDelayMs() {
        return tradingModeState != null && settingsVolatileMode != null
                && tradingModeState.getTradingMode() == TradingMode.VOLATILE
                && settingsVolatileMode.getActiveFields().contains(Field.signalDelayMs)
                ? settingsVolatileMode.getSignalDelayMs()
                : signalDelayMs;
    }

    public PlacingBlocks getPlacingBlocks() {
        return tradingModeState != null && settingsVolatileMode != null
                && tradingModeState.getTradingMode() == TradingMode.VOLATILE
                && settingsVolatileMode.getActiveFields().contains(Field.placingBlocks)
                ? settingsVolatileMode.getPlacingBlocks()
                : placingBlocks;
    }

    public PosAdjustment getPosAdjustment() {
        if (tradingModeState != null && settingsVolatileMode != null
                && tradingModeState.getTradingMode() == TradingMode.VOLATILE
                && settingsVolatileMode.getActiveFields().contains(Field.posAdjustment)
                && settingsVolatileMode.getPosAdjustment() != null) {
            final PosAdjustment res = this.posAdjustment.clone();
            res.setPosAdjustmentPlacingType(settingsVolatileMode.getPosAdjustment().getPosAdjustmentPlacingType());
            res.setPosAdjustmentMin(settingsVolatileMode.getPosAdjustment().getPosAdjustmentMin());
            res.setPosAdjustmentMax(settingsVolatileMode.getPosAdjustment().getPosAdjustmentMax());
            return res;
        }
        return posAdjustment;
    }

    public Boolean getAdjustByNtUsd() {
        return tradingModeState != null && settingsVolatileMode != null
                && tradingModeState.getTradingMode() == TradingMode.VOLATILE
                && settingsVolatileMode.getActiveFields().contains(Field.adjustByNtUsd)
                ? settingsVolatileMode.getAdjustByNtUsd()
                : adjustByNtUsd;
    }

    public ArbScheme getArbScheme() {
        return tradingModeState != null && settingsVolatileMode != null
                && tradingModeState.getTradingMode() == TradingMode.VOLATILE
                && settingsVolatileMode.getActiveFields().contains(Field.arb_scheme)
                ? settingsVolatileMode.getArbScheme()
                : arbScheme;
    }

    public ConBoPortions getConBoPortionsRaw() {
        return conBoPortions;
    }

    public ConBoPortions getConBoPortions() {
        return tradingModeState != null && settingsVolatileMode != null
                && tradingModeState.getTradingMode() == TradingMode.VOLATILE
                ? settingsVolatileMode.getConBoPortions(conBoPortions)
                : conBoPortions;

    }

    public PlacingType getLeftPlacingTypeRaw() {
        return leftPlacingType;
    }

    public PlacingType getRightPlacingTypeRaw() {
        return rightPlacingType;
    }

    public Integer getSignalDelayMsRaw() {
        return signalDelayMs;
    }

    public PlacingBlocks getPlacingBlocksRaw() {
        return placingBlocks;
    }

    public PosAdjustment getPosAdjustmentRaw() {
        return posAdjustment;
    }

    public Boolean getAdjustByNtUsdRaw() {
        return adjustByNtUsd;
    }

    public ArbScheme getArbSchemeRaw() {
        return arbScheme;
    }

    //    @Transient
//    private Boolean movingStopped;// for UI
    public boolean flagMovingStopped() {
        return extraFlags.contains(ExtraFlag.STOP_MOVING);
    }

    public boolean flagUpdateAvgPriceStopped() {
        return extraFlags.contains(ExtraFlag.STOP_UPDATE_AVG_PRICE);
    }
}
