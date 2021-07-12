package com.bitplay.persistance.domain.fluent;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.util.Date;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
@AllArgsConstructor
@NoArgsConstructor
public class LogRow {

    private LogLevel logLevel;
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "HH:mm:ss.SSS", timezone = "Europe/Moscow")
    private Date timestamp;
    private String theLog;

}
