package com.bitplay.persistance.domain.settings;

import com.bitplay.persistance.domain.AbstractDocument;

import org.springframework.data.annotation.TypeAlias;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Created by Sergey Shurmin on 11/27/17.
 */
@Document(collection = "settingsCollection")
@TypeAlias("settings")
public class Settings extends AbstractDocument {

    private ArbScheme arbScheme;

    public static Settings createDefault() {
        final Settings settings = new Settings();
        settings.arbScheme = ArbScheme.MT;
        return settings;
    }

    public ArbScheme getArbScheme() {
        return arbScheme;
    }

    public void setArbScheme(ArbScheme arbScheme) {
        this.arbScheme = arbScheme;
    }
}
