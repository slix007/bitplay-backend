package info.bitrich.xchangestream.bitmex.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public class WebSocketMessage {
    private final String op;
    private final List<String> args;

    public WebSocketMessage(@JsonProperty("op") String op, @JsonProperty("args") List<String> args) {
        this.op = op;
        this.args = args;
    }

    public String getOp() {
        return op;
    }

    public List<String> getArgs() {
        return args;
    }
}
