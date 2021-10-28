/*
 * SonarLint Core - Implementation
 * Copyright (C) 2016-2021 SonarSource SA
 * mailto:info AT sonarsource DOT com
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package org.sonarsource.sonarlint.core.container.connected.update;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.sonarsource.sonarlint.core.container.storage.GlobalSettingsStore;
import org.sonarsource.sonarlint.core.proto.Sonarlint.GlobalProperties;
import org.sonarsource.sonarlint.core.serverapi.ServerApi;
import org.sonarsource.sonarlint.core.serverapi.ServerApiHelper;
import org.sonarsource.sonarlint.core.serverapi.settings.SettingsApi;

public class SettingsDownloader {
  private final SettingsApi settingsApi;
  private final GlobalSettingsStore globalSettingsStore;

  public SettingsDownloader(ServerApiHelper serverApiHelper, GlobalSettingsStore globalSettingsStore) {
    this.settingsApi = new ServerApi(serverApiHelper).settings();
    this.globalSettingsStore = globalSettingsStore;
  }

  public List<UpdateEvent> fetchGlobalSettings(GlobalSettingsStore currentGlobalSettingsStore) {
    GlobalProperties newSettings = settingsApi.getGlobalSettings();
    Map<String, String> oldSettings = currentGlobalSettingsStore.getAllOrEmpty().getPropertiesMap();
    List<UpdateEvent> settingsEvents = new ArrayList<>();
    for (Map.Entry<String, String> entry : newSettings.getPropertiesMap().entrySet()) {
      if (oldSettings.containsKey(entry.getKey())) {
        String oldValue = oldSettings.get(entry.getKey());
        if (!oldValue.equals(entry.getValue())) {
          settingsEvents.add(new SettingsValueChanged(entry.getKey(), oldValue, entry.getValue()));
        }
      } else {
        settingsEvents.add(new SettingsAdded(entry.getKey(), entry.getValue()));
      }
    }
    for (Map.Entry<String, String> oldEntry : oldSettings.entrySet()) {
      if (!newSettings.getPropertiesMap().containsKey(oldEntry.getKey())) {
        settingsEvents.add(new SettingsRemoved(oldEntry.getKey(), oldEntry.getValue()));
      }
    }
    globalSettingsStore.store(newSettings);
    return settingsEvents;
  }

  public static class SettingsAdded implements UpdateEvent {
    private final String key;
    private final String value;

    public SettingsAdded(String key, String value) {
      this.key = key;
      this.value = value;
    }

    public String getKey() {
      return key;
    }

    public String getValue() {
      return value;
    }
  }

  public static class SettingsRemoved implements UpdateEvent {
    private final String key;
    private final String value;

    public SettingsRemoved(String key, String value) {
      this.key = key;
      this.value = value;
    }

    public String getKey() {
      return key;
    }

    public String getValue() {
      return value;
    }
  }

  public static class SettingsValueChanged implements UpdateEvent {
    private final String key;
    private final String oldValue;
    private final String newValue;

    public SettingsValueChanged(String key, String oldValue, String newValue) {
      this.key = key;
      this.oldValue = oldValue;
      this.newValue = newValue;
    }

    public String getKey() {
      return key;
    }

    public String getOldValue() {
      return oldValue;
    }

    public String getNewValue() {
      return newValue;
    }
  }
}
