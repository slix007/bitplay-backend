package com.bitplay.persistance.domain.fluent;

import com.bitplay.persistance.domain.AbstractDocument;
import com.bitplay.persistance.domain.settings.BitmexContractType;
import com.bitplay.persistance.domain.settings.OkexContractType;
import com.fasterxml.jackson.annotation.JsonFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.TypeAlias;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Created by Sergey Shurmin on 12/20/17.
 */
@Document(collection = "tradeSeries")
@TypeAlias("trade")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class FplayTrade extends AbstractDocument {

    private String counterName;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss.SSS(z)", timezone = "Europe/Moscow")
    private Date startTimestamp;

    private List<FplayOrder> bitmexOrders;
    private List<FplayOrder> okexOrders;

    private DeltaName deltaName;
    private TradeStatus tradeStatus;

    private BitmexContractType bitmexContractType;
    private OkexContractType okexContractType;

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

    @Override
    public String toString() {
        return "FplayTrade{" +
                "tradeId='" + getId() + '\'' +
                "counterName='" + counterName + '\'' +
                ", startTimestamp=" + startTimestamp +
                ", bitmexOrders=" + bitmexOrders +
                ", okexOrders=" + okexOrders +
                ", deltaName=" + deltaName +
                ", tradeStatus=" + tradeStatus +
                ", bitmexContractType=" + bitmexContractType +
                ", okexContractType=" + okexContractType +
                ", deltaLog=" + deltaLog +
                '}';
    }
}
