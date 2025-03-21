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
package org.sonarsource.sonarlint.core.plugin.commons.api;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import javax.annotation.CheckForNull;
import javax.annotation.Nullable;
import org.sonarsource.sonarlint.core.commons.api.SonarLanguage;

public interface SkipReason {

  class UnsupportedFeature implements SkipReason {

    public static final UnsupportedFeature INSTANCE = new UnsupportedFeature();

    private UnsupportedFeature() {
      // Singleton
    }

  }

  class IncompatiblePluginApi implements SkipReason {

    public static final IncompatiblePluginApi INSTANCE = new IncompatiblePluginApi();

    private IncompatiblePluginApi() {
      // Singleton
    }

  }

  class LanguagesNotEnabled implements SkipReason {
    private final Set<SonarLanguage> languages;

    public LanguagesNotEnabled(Collection<SonarLanguage> languages) {
      this.languages = new LinkedHashSet<>(languages);
    }

    public Set<SonarLanguage> getNotEnabledLanguages() {
      return languages;
    }

    @Override
    public int hashCode() {
      return Objects.hash(languages);
    }

    @Override
    public boolean equals(Object obj) {
      if (this == obj) {
        return true;
      }
      if (!(obj instanceof LanguagesNotEnabled other)) {
        return false;
      }
      return Objects.equals(languages, other.languages);
    }

    @Override
    public String toString() {
      return "LanguagesNotEnabled [languages=" + languages + "]";
    }

  }

  class UnsatisfiedDependency implements SkipReason {
    private final String dependencyKey;

    public UnsatisfiedDependency(String dependencyKey) {
      this.dependencyKey = dependencyKey;
    }

    public String getDependencyKey() {
      return dependencyKey;
    }

    @Override
    public int hashCode() {
      return Objects.hash(dependencyKey);
    }

    @Override
    public boolean equals(Object obj) {
      if (this == obj) {
        return true;
      }
      if (!(obj instanceof UnsatisfiedDependency other)) {
        return false;
      }
      return Objects.equals(dependencyKey, other.dependencyKey);
    }

    @Override
    public String toString() {
      return "UnsatisfiedDependency [dependencyKey=" + dependencyKey + "]";
    }

  }

  class UnsatisfiedRuntimeRequirement implements SkipReason {
    public enum RuntimeRequirement {
      JRE,
      NODEJS
    }

    private final RuntimeRequirement runtime;
    private final String currentVersion;
    private final String minVersion;

    public UnsatisfiedRuntimeRequirement(RuntimeRequirement runtime, @Nullable String currentVersion, String minVersion) {
      this.runtime = runtime;
      this.currentVersion = currentVersion;
      this.minVersion = minVersion;
    }

    public RuntimeRequirement getRuntime() {
      return runtime;
    }

    @CheckForNull
    public String getCurrentVersion() {
      return currentVersion;
    }

    public String getMinVersion() {
      return minVersion;
    }

    @Override
    public int hashCode() {
      return Objects.hash(runtime, currentVersion, minVersion);
    }

    @Override
    public boolean equals(Object obj) {
      if (this == obj) {
        return true;
      }
      if (!(obj instanceof UnsatisfiedRuntimeRequirement other)) {
        return false;
      }
      return runtime == other.runtime && Objects.equals(currentVersion, other.currentVersion) && Objects.equals(minVersion, other.minVersion);
    }

    @Override
    public String toString() {
      return "UnsatisfiedRuntimeRequirement [runtime=" + runtime + ", currentVersion=" + currentVersion + ", minVersion=" + minVersion + "]";
    }

  }

}
