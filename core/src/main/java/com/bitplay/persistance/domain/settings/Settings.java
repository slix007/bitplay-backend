package com.bitplay.persistance.domain.settings;

import com.bitplay.persistance.domain.AbstractDocument;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import org.springframework.data.annotation.TypeAlias;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Created by Sergey Shurmin on 11/27/17.
 */
@Document(collection = "settingsCollection")
@TypeAlias("settings")
@JsonIgnoreProperties(ignoreUnknown = true)
public class Settings extends AbstractDocument {

    private ArbScheme arbScheme;
    private SysOverloadArgs bitmexSysOverloadArgs;
//    private SysOverloadArgs okexSysOverloadArgs;

    public static Settings createDefault() {
        final Settings settings = new Settings();
        settings.arbScheme = ArbScheme.MT;
        settings.bitmexSysOverloadArgs = SysOverloadArgs.defaults();
//        settings.okexSysOverloadArgs = SysOverloadArgs.defaults();
        settings.setId(1L);
        return settings;
    }

    public ArbScheme getArbScheme() {
        return arbScheme;
    }

    public void setArbScheme(ArbScheme arbScheme) {
        this.arbScheme = arbScheme;
    }

    public SysOverloadArgs getBitmexSysOverloadArgs() {
        return bitmexSysOverloadArgs;
    }

    public void setBitmexSysOverloadArgs(SysOverloadArgs bimexSysOverloadArgs) {
        this.bitmexSysOverloadArgs = bimexSysOverloadArgs;
    }

    @Override
    public String toString() {
        return "Settings{" +
                "arbScheme=" + arbScheme +
                ", bitmexSysOverloadArgs=" + bitmexSysOverloadArgs +
                '}';
    }
}
