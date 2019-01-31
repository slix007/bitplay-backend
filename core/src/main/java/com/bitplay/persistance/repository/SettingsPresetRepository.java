package com.bitplay.persistance.repository;

import com.bitplay.persistance.domain.settings.SettingsPreset;
import java.util.List;
import org.springframework.data.mongodb.repository.MongoRepository;

/**
 * Created by Sergey Shurmin on 11/27/17.
 */
public interface SettingsPresetRepository extends MongoRepository<SettingsPreset, Long> {

    SettingsPreset findFirstByName(String name);

    List<SettingsPreset> deleteByName(String name);
}
