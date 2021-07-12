package com.bitplay.persistance;

import com.bitplay.persistance.domain.settings.BitmexContractType;
import com.bitplay.persistance.domain.settings.BitmexCtList;
import com.bitplay.persistance.domain.settings.ContractType;
import com.bitplay.persistance.domain.settings.ExtraFlag;
import com.bitplay.persistance.domain.settings.OkexContractType;
import com.bitplay.persistance.domain.settings.Settings;
import com.bitplay.persistance.domain.settings.TradingMode;
import com.bitplay.persistance.repository.SettingsRepository;
import com.bitplay.arbitrage.ArbitrageService;
import com.bitplay.arbitrage.events.ArbitrageReadyEvent;
import com.bitplay.market.MarketStaticData;
import com.bitplay.xchange.currency.CurrencyPair;
import com.mongodb.WriteResult;
import java.math.BigDecimal;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.data.mongodb.core.MongoOperations;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

/**
 * Created by Sergey Shurmin on 12/4/17.
 */
@Service
public class SettingsRepositoryService {

    private MongoOperations mongoOperation;

    @Autowired
    private SettingsRepository settingsRepository;

    @Autowired
    private ArbitrageService arbitrageService;

    @Autowired
    public SettingsRepositoryService(MongoOperations mongoOperation) {
        this.mongoOperation = mongoOperation;
    }

    private volatile Settings settings;
    private static volatile boolean invalidated = true;

    @EventListener(ArbitrageReadyEvent.class)
    public void init() {
        settings = fetchSettings(); // in case of mongo ChangeSets
    }

    public static void setInvalidated() {
        invalidated = true;
    }

    public Settings getSettings() {

        if (settings == null || invalidated) {
            settings = fetchSettings();
            invalidated = false;
        }

        setTransientCm();

        return settings;
    }

    private void setTransientCm() {
        settings.getPlacingBlocks().setCm(arbitrageService.getCm());
        settings.getPlacingBlocks().setEth(arbitrageService.isEth());
        if (arbitrageService.getLeftMarketService() != null) {
            settings.getPlacingBlocks().setLeftOkex(arbitrageService.getLeftMarketService().getMarketStaticData() == MarketStaticData.OKEX);
        }
    }

    private Settings fetchSettings() {
        Settings one = settingsRepository.findOne(1L);
        if (one == null) {
            one = Settings.createDefault();
        }
        return one;
    }

    public void saveSettings(Settings settings) {
        settingsRepository.save(settings);
        this.settings = settings;
    }

    public Settings updateVolatileDurationSec(Integer volatileDurationSec) {
        return update(null, volatileDurationSec);
    }

    public Settings updateTradingModeState(TradingMode tradingMode) {
        return update(tradingMode, null);
    }

    private Settings update(TradingMode tradingMode, Integer volatileDurationSec) {
        Query query = new Query().addCriteria(Criteria.where("_id").exists(true).andOperator(Criteria.where("_id").is(1L)));
        Update update = new Update();
        update.set("tradingModeState.timestamp", new Date());
        if (volatileDurationSec != null) {
            update.set("settingsVolatileMode.volatileDurationSec", volatileDurationSec);
        }
        if (tradingMode != null) {
            update.set("tradingModeState.tradingMode", tradingMode);
        }
        final WriteResult writeResult = mongoOperation.updateFirst(query, update, Settings.class);
        this.settings = fetchSettings();
        return this.settings;
    }

    public Settings removeExtraFlag(ExtraFlag extraFlag) {
        Query query = new Query().addCriteria(Criteria.where("_id").exists(true).andOperator(Criteria.where("_id").is(1L)));
        Update update = new Update();
        update.pull("extraFlags", extraFlag);
        final WriteResult writeResult = mongoOperation.updateFirst(query, update, Settings.class);

        this.settings = fetchSettings();
        return this.settings;
    }

    public Settings addExtraFlag(ExtraFlag extraFlag) {
        Query query = new Query().addCriteria(Criteria.where("_id").exists(true).andOperator(Criteria.where("_id").is(1L)));
        Update update = new Update();
        update.addToSet("extraFlags", extraFlag);
        final WriteResult writeResult = mongoOperation.updateFirst(query, update, Settings.class);

        this.settings = fetchSettings();
        return this.settings;
    }

    public Settings updateHedge(BigDecimal btcHedge, BigDecimal ethHedge) {
        Query query = new Query().addCriteria(Criteria.where("_id").exists(true).andOperator(Criteria.where("_id").is(1L)));
        Update update = new Update();
        if (btcHedge != null) {
            update.set("hedgeBtc", btcHedge);
        }
        if (ethHedge != null) {
            update.set("hedgeEth", ethHedge);
        }
        final WriteResult writeResult = mongoOperation.updateFirst(query, update, Settings.class);
        this.settings = fetchSettings();
        return this.settings;
    }

    public CurrencyPair getCurrencyPair(ContractType type) {
        if (type instanceof OkexContractType) {
            return type.getCurrencyPair();
        }
        // else Bitmex
        final BitmexContractType btmType = (BitmexContractType) type;
        final Settings s = getSettings();
        final BitmexCtList types = s.getBitmexContractTypes();
        final String secondCurrency;
        switch (btmType) {
            case XBTUSD_Perpetual:
            case ETHUSD_Perpetual:
            case XRPUSD_Perpetual:
            case LTCUSD_Perpetual:
            case BCHUSD_Perpetual:
                secondCurrency = "USD";
                break;
            case LINKUSDT_Perpetual:
                secondCurrency = "USDT";
                break;
            case XBTUSD_Quarter:
                secondCurrency = types.getBtcUsdQuoter().substring(3);
                break;
            case XBTUSD_BiQuarter:
                secondCurrency = types.getBtcUsdBiQuoter().substring(3);
                break;
            case ETHUSD_Quarter:
                secondCurrency = types.getEthUsdQuoter().substring(3);
                break;
            default:
                secondCurrency = "";
        }
        return new CurrencyPair(btmType.getFirstCurrency(), secondCurrency);
    }

    public Map<String, String> getBitmexContractNames() {
        final Map<String, String> map = new HashMap<>();
        for (BitmexContractType type : BitmexContractType.values()) {
            final CurrencyPair currencyPair = getCurrencyPair(type);
            final String symbol = currencyPair.base.getCurrencyCode() + currencyPair.counter.getCurrencyCode();
            map.put(type.getName(), symbol);
        }
        return map;

    }

    public void updateVolatileAutoBorders(BigDecimal bcd, BigDecimal leftAddBorder, BigDecimal rightAddBorder) {
        Query query = new Query().addCriteria(Criteria.where("_id").exists(true).andOperator(Criteria.where("_id").is(1L)));
        Update update = new Update();
        if (bcd != null) {
            update.set("settingsVolatileMode.borderCrossDepth", bcd);
        }
        if (leftAddBorder != null) {
            update.set("settingsVolatileMode.bAddBorder", leftAddBorder);
        }
        if (rightAddBorder != null) {
            update.set("settingsVolatileMode.oAddBorder", rightAddBorder);
        }
        mongoOperation.updateFirst(query, update, Settings.class);
    }

}
