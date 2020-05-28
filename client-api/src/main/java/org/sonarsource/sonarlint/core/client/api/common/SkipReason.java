/*
 * SonarLint Core - Client API
 * Copyright (C) 2009-2020 SonarSource SA
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
package org.sonarsource.sonarlint.core.client.api.common;

import java.util.Objects;

public interface SkipReason {

  class IncompatiblePluginApi implements SkipReason {

    public static final IncompatiblePluginApi INSTANCE = new IncompatiblePluginApi();

    private IncompatiblePluginApi() {
      // Singleton
    }

  }

  class LanguageNotEnabled implements SkipReason {
    private final String languageKey;

    public LanguageNotEnabled(String notEnabledLanguageKey) {
      this.languageKey = notEnabledLanguageKey;
    }

    public String getNotEnabledLanguageKey() {
      return languageKey;
    }

    @Override
    public int hashCode() {
      return Objects.hash(languageKey);
    }

    @Override
    public boolean equals(Object obj) {
      if (this == obj) {
        return true;
      }
      if (!(obj instanceof LanguageNotEnabled)) {
        return false;
      }
      LanguageNotEnabled other = (LanguageNotEnabled) obj;
      return Objects.equals(languageKey, other.languageKey);
    }

    @Override
    public String toString() {
      StringBuilder builder = new StringBuilder();
      builder.append("LanguageNotEnabled [languageKey=").append(languageKey).append("]");
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
      UnsatisfiedDependency other = (UnsatisfiedDependency) obj;
      return Objects.equals(dependencyKey, other.dependencyKey);
    }

    @Override
    public String toString() {
      StringBuilder builder = new StringBuilder();
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
      IncompatiblePluginVersion other = (IncompatiblePluginVersion) obj;
      return Objects.equals(minVersion, other.minVersion);
    }

    @Override
    public String toString() {
      StringBuilder builder = new StringBuilder();
      builder.append("IncompatiblePluginVersion [minVersion=").append(minVersion).append("]");
      return builder.toString();
    }

  }

}
