/*
 * SonarLint Core - Commons
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
package org.sonarsource.sonarlint.core.commons.plugins;

import java.util.Arrays;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.sonarsource.sonarlint.core.commons.api.SonarLanguage;

public enum SonarPluginDependency implements SonarArtifact {
  OMNISHARP_MONO("omnisharp-mono"),
  OMNISHARP_NET472("omnisharp-net472"),
  OMNISHARP_NET6("omnisharp-net6");

  public static Optional<SonarPluginDependency> findByKey(String key) {
    return Arrays.stream(values()).filter(p -> p.key.equals(key)).findFirst();
  }

  private final String key;

  SonarPluginDependency(String key) {
    this.key = key;
  }

  @Override
  public String getKey() {
    return key;
  }

  /**
   * All current dependency artifacts are Omnisharp-related and support C# only.
   * Computed lazily to avoid circular static initialization between SonarLanguage,
   * SonarPlugin, and SonarPluginDependency.
   */
  @Override
  public Set<SonarLanguage> getLanguages() {
    return Set.of(SonarLanguage.CS);
  }

  public Set<SonarPlugin> getDependents() {
    return Arrays.stream(SonarPlugin.values())
      .filter(plugin -> plugin.getDependencies().stream().anyMatch(dep -> dep.artifact().equals(this)))
      .collect(Collectors.toSet());
  }
}
