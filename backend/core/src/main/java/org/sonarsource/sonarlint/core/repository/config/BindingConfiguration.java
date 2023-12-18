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
package org.sonarsource.sonarlint.core.repository.config;

import java.util.Objects;
import java.util.Optional;
import java.util.function.BiFunction;
import javax.annotation.CheckForNull;
import javax.annotation.Nullable;

public class BindingConfiguration {

  private final String connectionId;
  private final String sonarProjectKey;
  private final boolean bindingSuggestionDisabled;

  public BindingConfiguration(@Nullable String connectionId, @Nullable String sonarProjectKey, boolean bindingSuggestionDisabled) {
    this.connectionId = connectionId;
    this.sonarProjectKey = sonarProjectKey;
    this.bindingSuggestionDisabled = bindingSuggestionDisabled;
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

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    var that = (BindingConfiguration) o;
    return bindingSuggestionDisabled == that.bindingSuggestionDisabled
      && Objects.equals(connectionId, that.connectionId)
      && Objects.equals(sonarProjectKey, that.sonarProjectKey);
  }

  @Override
  public int hashCode() {
    return Objects.hash(connectionId, sonarProjectKey, bindingSuggestionDisabled);
  }

}
