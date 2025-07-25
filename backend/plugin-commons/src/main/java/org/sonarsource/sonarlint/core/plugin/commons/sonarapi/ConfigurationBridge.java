/*
 * SonarLint Core - Plugin Commons
 * Copyright (C) 2016-2025 SonarSource SA
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
package org.sonarsource.sonarlint.core.plugin.commons.sonarapi;

import java.util.Optional;
import org.sonar.api.config.Configuration;
import org.sonar.api.config.Settings;

/**
 * Used to help migration from {@link Settings} to {@link Configuration}
 */
public class ConfigurationBridge implements Configuration {

  private final Settings settings;

  public ConfigurationBridge(Settings settings) {
    this.settings = settings;
  }

  @Override
  public Optional<String> get(String key) {
    return Optional.ofNullable(settings.getString(key));
  }

  @Override
  public boolean hasKey(String key) {
    return settings.hasKey(key);
  }

  @Override
  public String[] getStringArray(String key) {
    return settings.getStringArray(key);
  }

}
