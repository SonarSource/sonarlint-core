/*
 * SonarLint Core - Implementation
 * Copyright (C) 2016-2023 SonarSource SA
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
package org.sonarsource.sonarlint.core.event;

import java.util.Objects;
import javax.annotation.CheckForNull;
import javax.annotation.Nullable;

public class BindingConfigChangedEvent {

  private final BindingConfig previousConfig;
  private final BindingConfig newConfig;

  public BindingConfigChangedEvent(BindingConfig previousConfig, BindingConfig newConfig) {
    this.previousConfig = previousConfig;
    this.newConfig = newConfig;
  }

  public BindingConfig getPreviousConfig() {
    return previousConfig;
  }

  public BindingConfig getNewConfig() {
    return newConfig;
  }

  public static class BindingConfig {
    private final String configScopeId;
    private final String connectionId;
    private final String sonarProjectKey;
    private final boolean bindingSuggestionDisabled;

    public BindingConfig(String configScopeId, @Nullable String connectionId, @Nullable String sonarProjectKey, boolean bindingSuggestionDisabled) {
      this.configScopeId = configScopeId;
      this.connectionId = connectionId;
      this.sonarProjectKey = sonarProjectKey;
      this.bindingSuggestionDisabled = bindingSuggestionDisabled;
    }

    public String getConfigScopeId() {
      return configScopeId;
    }

    @CheckForNull
    public String getConnectionId() {
      return connectionId;
    }

    @CheckForNull
    public String getSonarProjectKey() {
      return sonarProjectKey;
    }

    public boolean isBindingSuggestionDisabled() {
      return bindingSuggestionDisabled;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      var that = (BindingConfig) o;
      return bindingSuggestionDisabled == that.bindingSuggestionDisabled
        && configScopeId.equals(that.configScopeId)
        && Objects.equals(connectionId, that.connectionId)
        && Objects.equals(sonarProjectKey, that.sonarProjectKey);
    }

    @Override
    public int hashCode() {
      return Objects.hash(configScopeId, connectionId, sonarProjectKey, bindingSuggestionDisabled);
    }
  }

}
