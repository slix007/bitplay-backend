package com.bitplay.persistance.domain.fluent;

import com.bitplay.persistance.domain.settings.BitmexContractType;
import com.bitplay.persistance.domain.settings.OkexContractType;
import com.fasterxml.jackson.annotation.JsonFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.TypeAlias;
import org.springframework.data.mongodb.core.mapping.Document;

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
    private Date updated;
    private Long version;

    private DeltaName deltaName;
    private TradeStatus tradeStatus;
    private TradeMStatus bitmexStatus;
    private TradeMStatus okexStatus;

    private BitmexContractType bitmexContractType;
    private OkexContractType okexContractType;

    private List<String> bitmexOrders;
    private List<String> okexOrders;
    private List<LogRow> deltaLog;

    public List<String> getBitmexOrders() {
        return bitmexOrders != null ? bitmexOrders : new ArrayList<>();
    }

    public List<String> getOkexOrders() {
        return okexOrders != null ? okexOrders : new ArrayList<>();
    }

    public List<LogRow> getDeltaLog() {
        return deltaLog != null ? deltaLog : new ArrayList<>();
    }

    public boolean isBothCompleded() {
        return bitmexStatus == TradeMStatus.COMPLETED && okexStatus == TradeMStatus.COMPLETED;
    }
}
