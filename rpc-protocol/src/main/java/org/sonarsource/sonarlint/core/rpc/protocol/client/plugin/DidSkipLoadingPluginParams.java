/*
 * SonarLint Core - RPC Protocol
 * Copyright (C) 2016-2024 SonarSource SA
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
package org.sonarsource.sonarlint.core.rpc.protocol.client.plugin;

import javax.annotation.CheckForNull;
import javax.annotation.Nullable;
import org.sonarsource.sonarlint.core.rpc.protocol.common.Language;

public class DidSkipLoadingPluginParams {
  private final String configurationScopeId;
  private final Language language;
  private final SkipReason reason;
  private final String minVersion;
  private final String currentVersion;

  public DidSkipLoadingPluginParams(String configurationScopeId, Language language, SkipReason reason, String minVersion, @Nullable String currentVersion) {
    this.configurationScopeId = configurationScopeId;
    this.language = language;
    this.reason = reason;
    this.minVersion = minVersion;
    this.currentVersion = currentVersion;
  }

  public String getConfigurationScopeId() {
    return configurationScopeId;
  }

  public Language getLanguage() {
    return language;
  }

  public SkipReason getReason() {
    return reason;
  }

  public String getMinVersion() {
    return minVersion;
  }

  @CheckForNull
  public String getCurrentVersion() {
    return currentVersion;
  }

  public enum SkipReason {
    UNSATISFIED_JRE, UNSATISFIED_NODE_JS
  }
}
