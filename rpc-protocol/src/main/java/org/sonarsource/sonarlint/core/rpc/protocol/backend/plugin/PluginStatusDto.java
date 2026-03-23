/*
 * SonarLint Core - RPC Protocol
 * Copyright (C) 2016-2025 SonarSource Sàrl
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
package org.sonarsource.sonarlint.core.rpc.protocol.backend.plugin;

import javax.annotation.Nullable;
import org.sonarsource.sonarlint.core.rpc.protocol.common.Language;

public class PluginStatusDto {

  private final Language language;
  private final String pluginName;
  private final PluginStateDto state;
  @Nullable
  private final ArtifactSourceDto source;
  @Nullable
  private final String actualVersion;
  @Nullable
  private final String overriddenVersion;
  @Nullable
  private final String serverVersion;

  /**
   * @param language          language that this plugin provides analysis for
   * @param pluginName        human-readable name of the language/analyzer (e.g. "Java", "C/C++/Objective-C")
   * @param state             current lifecycle state of the plugin in the backend
   * @param source            where the plugin artifact came from; {@code null} when the plugin is not available
   * @param actualVersion     version of the plugin that is currently in use; {@code null} when the plugin is not loaded
   * @param overriddenVersion a local plugin version that was superseded by the one obtained via SQS/SQC sync, if any;
   *                          {@code null} when no override is in effect
   * @param serverVersion     version of the SonarQube Server that provided this plugin (e.g. "10.8.1");
   *                          {@code null} for non-server sources (embedded, cloud, unavailable)
   */
  public PluginStatusDto(Language language, String pluginName, PluginStateDto state, @Nullable ArtifactSourceDto source,
    @Nullable String actualVersion, @Nullable String overriddenVersion, @Nullable String serverVersion) {
    this.language = language;
    this.pluginName = pluginName;
    this.state = state;
    this.source = source;
    this.actualVersion = actualVersion;
    this.overriddenVersion = overriddenVersion;
    this.serverVersion = serverVersion;
  }

  public Language getLanguage() {
    return language;
  }

  public String getPluginName() {
    return pluginName;
  }

  public PluginStateDto getState() {
    return state;
  }

  @Nullable
  public ArtifactSourceDto getSource() {
    return source;
  }

  @Nullable
  public String getActualVersion() {
    return actualVersion;
  }

  @Nullable
  public String getOverriddenVersion() {
    return overriddenVersion;
  }

  @Nullable
  public String getServerVersion() {
    return serverVersion;
  }

}
