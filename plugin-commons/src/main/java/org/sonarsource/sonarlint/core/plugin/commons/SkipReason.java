/*
 * SonarLint Core - Plugin Commons
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
package org.sonarsource.sonarlint.core.plugin.commons;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import javax.annotation.CheckForNull;
import javax.annotation.Nullable;
import org.sonarsource.sonarlint.core.commons.Language;

public interface SkipReason {

  class IncompatiblePluginApi implements SkipReason {

    public static final IncompatiblePluginApi INSTANCE = new IncompatiblePluginApi();

    private IncompatiblePluginApi() {
      // Singleton
    }

  }

  class LanguagesNotEnabled implements SkipReason {
    private final Set<Language> languages;

    public LanguagesNotEnabled(Collection<Language> languages) {
      this.languages = new LinkedHashSet<>(languages);
    }

    public Set<Language> getNotEnabledLanguages() {
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
      if (!(obj instanceof LanguagesNotEnabled)) {
        return false;
      }
      var other = (LanguagesNotEnabled) obj;
      return Objects.equals(languages, other.languages);
    }

    @Override
    public String toString() {
      var builder = new StringBuilder();
      builder.append("LanguagesNotEnabled [languages=").append(languages).append("]");
      return builder.toString();
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
      if (!(obj instanceof UnsatisfiedDependency)) {
        return false;
      }
      var other = (UnsatisfiedDependency) obj;
      return Objects.equals(dependencyKey, other.dependencyKey);
    }

    @Override
    public String toString() {
      var builder = new StringBuilder();
      builder.append("UnsatisfiedDependency [dependencyKey=").append(dependencyKey).append("]");
      return builder.toString();
    }

  }

  class IncompatiblePluginVersion implements SkipReason {
    private final String minVersion;

    public IncompatiblePluginVersion(String minVersion) {
      this.minVersion = minVersion;
    }

    public String getMinVersion() {
      return minVersion;
    }

    @Override
    public int hashCode() {
      return Objects.hash(minVersion);
    }

    @Override
    public boolean equals(Object obj) {
      if (this == obj) {
        return true;
      }
      if (!(obj instanceof IncompatiblePluginVersion)) {
        return false;
      }
      var other = (IncompatiblePluginVersion) obj;
      return Objects.equals(minVersion, other.minVersion);
    }

    @Override
    public String toString() {
      var builder = new StringBuilder();
      builder.append("IncompatiblePluginVersion [minVersion=").append(minVersion).append("]");
      return builder.toString();
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
      if (!(obj instanceof UnsatisfiedRuntimeRequirement)) {
        return false;
      }
      var other = (UnsatisfiedRuntimeRequirement) obj;
      return runtime == other.runtime && Objects.equals(currentVersion, other.currentVersion) && Objects.equals(minVersion, other.minVersion);
    }

    @Override
    public String toString() {
      var builder = new StringBuilder();
      builder.append("UnsatisfiedRuntimeRequirement [runtime=").append(runtime).append(", currentVersion=").append(currentVersion).append(", minVersion=").append(minVersion)
        .append("]");
      return builder.toString();
    }

  }

}
