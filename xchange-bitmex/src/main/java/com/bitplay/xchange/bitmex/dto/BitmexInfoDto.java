package com.bitplay.xchange.bitmex.dto;

import java.util.Date;

/**
 * Created by Sergey Shurmin on 10/27/17.
 */
public class BitmexInfoDto implements HeadersAware {

    String name;
    String version;
    Date timestamp;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public Date getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Date timestamp) {
        this.timestamp = timestamp;
    }
}
