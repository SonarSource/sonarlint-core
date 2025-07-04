/*
 * SonarLint Core - Implementation
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
package org.sonarsource.sonarlint.core.repository.config;

import java.util.Objects;
import java.util.Optional;
import java.util.function.BiFunction;
import javax.annotation.Nullable;

public record BindingConfiguration(@Nullable String connectionId, @Nullable String sonarProjectKey, boolean bindingSuggestionDisabled) {

  public static BindingConfiguration noBinding() {
    return noBinding(false);
  }

  public static BindingConfiguration noBinding(boolean bindingSuggestionDisabled) {
    return new BindingConfiguration(null, null, bindingSuggestionDisabled);
  }

  public boolean isBound() {
    return connectionId != null && sonarProjectKey != null;
  }

  public boolean isBoundTo(String connectionId, String projectKey) {
    return Objects.equals(connectionId, this.connectionId) && Objects.equals(projectKey, this.sonarProjectKey);
  }

  public boolean isBoundToConnection(String connectionId) {
    return Objects.equals(connectionId, this.connectionId) && sonarProjectKey != null;
  }

  public boolean isBoundToProject(String projectKey) {
    return connectionId != null && Objects.equals(projectKey, this.sonarProjectKey);
  }

  public <G> Optional<G> ifBound(BiFunction<String, String, G> calledIfBound) {
    if (isBound()) {
      return Optional.of(calledIfBound.apply(connectionId, sonarProjectKey));
    }
    return Optional.empty();
  }

}
