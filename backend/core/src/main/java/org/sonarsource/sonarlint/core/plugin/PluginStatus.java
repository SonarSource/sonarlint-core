/*
 * SonarLint Core - Implementation
 * Copyright (C) SonarSource Sàrl
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
package org.sonarsource.sonarlint.core.plugin;

import java.nio.file.Path;
import javax.annotation.Nullable;
import org.sonarsource.sonarlint.core.commons.Version;
import org.sonarsource.sonarlint.core.commons.api.SonarLanguage;

/**
 * @param pluginKey         the plugin key
 * @param language          language that this plugin provides analysis for
 * @param state             current state of the plugin at the backend
 * @param source            source where the plugin jar came from
 * @param actualVersion     used version of the plugin
 * @param overriddenVersion a version of the plugin that is overridden by the actualVersion, if any
 * @param path              path to the plugin jar on disk; populated for SYNCED/ACTIVE, null for DOWNLOADING/FAILED
 * @param serverVersion     version of the SonarQube Server that provided this plugin; {@code null} for non-server sources
 */
public record PluginStatus(
  String pluginKey,
  @Nullable SonarLanguage language,
  ArtifactState state,
  @Nullable ArtifactSource source,
  @Nullable Version actualVersion,
  @Nullable Version overriddenVersion,
  @Nullable Path path,
  @Nullable String serverVersion) {

  public static PluginStatus forLanguage(SonarLanguage language, ArtifactState state,
    @Nullable ArtifactSource source, @Nullable Version actual, @Nullable Version overridden, @Nullable Path path,
    @Nullable String serverVersion) {
    return new PluginStatus(language.getPluginKey(), language, state, source, actual, overridden, path, serverVersion);
  }

  public static PluginStatus forCompanion(String pluginKey, ArtifactState state,
    @Nullable ArtifactSource source, @Nullable Path path, @Nullable String serverVersion) {
    return new PluginStatus(pluginKey, null, state, source, null, null, path, serverVersion);
  }

  public static PluginStatus unsupported(SonarLanguage language) {
    return forLanguage(language, ArtifactState.UNSUPPORTED, null, null, null, null, null);
  }

  public String pluginName() {
    return language != null ? language.getName() : pluginKey;
  }

}
