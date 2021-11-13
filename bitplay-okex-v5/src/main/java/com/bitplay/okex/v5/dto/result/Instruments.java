package com.bitplay.okex.v5.dto.result;

import java.util.List;
import lombok.Data;

@Data
public class Instruments {

    private String code;
    private String msg;
    private List<InstrumentsData> data;

    @Data
    public static class InstrumentsData {

        private String instId;
        private String instType;
        private String alias;

        public String getInstId() {
            return instId;
        }

        public void setInstId(String instId) {
            this.instId = instId;
        }

        public String getInstType() {
            return instType;
        }

        public void setInstType(String instType) {
            this.instType = instType;
        }

        public String getAlias() {
            return alias;
        }

        public void setAlias(String alias) {
            this.alias = alias;
        }
    }
}
