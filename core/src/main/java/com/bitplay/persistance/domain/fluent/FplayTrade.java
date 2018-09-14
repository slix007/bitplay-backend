package com.bitplay.persistance.domain.fluent;

import com.bitplay.persistance.domain.AbstractDocument;
import com.fasterxml.jackson.annotation.JsonFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.springframework.data.annotation.TypeAlias;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Created by Sergey Shurmin on 12/20/17.
 */
@Document(collection = "tradeSeries")
@TypeAlias("trade")
@Getter
@Setter
@ToString
@AllArgsConstructor
@NoArgsConstructor
public class FplayTrade extends AbstractDocument {

    private String counterName;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss.SSS(z)")
    private Date startTimestamp;

    private List<FplayOrder> bitmexOrders;
    private List<FplayOrder> okexOrders;

    private DeltaName deltaName;
    private TradeStatus tradeStatus;

    private List<LogRow> deltaLog;

    public List<FplayOrder> getBitmexOrders() {
        return bitmexOrders != null ? bitmexOrders : new ArrayList<>();
    }

    public List<FplayOrder> getOkexOrders() {
        return okexOrders != null ? okexOrders : new ArrayList<>();
    }

    public List<LogRow> getDeltaLog() {
        return deltaLog != null ? deltaLog : new ArrayList<>();
    }
}
