package com.bitplay.persistance.domain.fluent;

import com.bitplay.persistance.domain.settings.ContractType;
import com.bitplay.persistance.domain.settings.TradingMode;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.TypeAlias;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Created by Sergey Shurmin on 12/20/17.
 */
@Document(collection = "tradeSeries")
@TypeAlias("trade")
@Data
@AllArgsConstructor
@NoArgsConstructor
public class FplayTrade {

    @Id
    private Long id;

    private String counterName;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss.SSS(z)", timezone = "Europe/Moscow")
    private Date startTimestamp;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss.SSS(z)", timezone = "Europe/Moscow")
    @Field
    @Indexed(expireAfterSeconds = 3600 * 24 * 31) // one month
    private Date updated;
    private Long version;

    private DeltaName deltaName;
    private TradeStatus tradeStatus;
    private TradeMStatus bitmexStatus;
    private Date bitmexFinishTime;
    private TradeMStatus okexStatus;
    private List<String> tradeStatusUpdates;
    private List<String> bitmexStatusUpdates;
    private List<String> okexStatusUpdates;

    private TradingMode tradingMode;

    private ContractType leftContractType;
    private ContractType rightContractType;

    private List<String> bitmexOrders;
    private List<String> okexOrders;
    private List<LogRow> deltaLog;

    private FplayTradeMon fplayTradeMon;

    public List<String> getBitmexOrders() {
        return bitmexOrders != null ? bitmexOrders : new ArrayList<>();
    }

    public List<String> getOkexOrders() {
        return okexOrders != null ? okexOrders : new ArrayList<>();
    }

    public List<LogRow> getDeltaLog() {
        return deltaLog != null ? deltaLog : new ArrayList<>();
    }

    public boolean isBothCompleted() {
        return bitmexStatus == TradeMStatus.FINISHED && okexStatus == TradeMStatus.FINISHED;
    }
}
