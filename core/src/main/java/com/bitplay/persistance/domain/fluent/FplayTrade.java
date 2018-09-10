package com.bitplay.persistance.domain.fluent;

import com.bitplay.persistance.domain.AbstractDocument;
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
@Document(collection = "tradeCollection")
@TypeAlias("trade")
@Getter
@Setter
@ToString
@AllArgsConstructor
@NoArgsConstructor
public class FplayTrade extends AbstractDocument {

    private String counterName;

    private Date startTimestamp;

    private List<FplayOrder> bitmexOrders;
    private List<FplayOrder> okexOrders;


}
