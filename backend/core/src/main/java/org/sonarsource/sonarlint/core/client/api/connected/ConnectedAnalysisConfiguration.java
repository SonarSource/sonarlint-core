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
package org.sonarsource.sonarlint.core.client.api.connected;

import javax.annotation.concurrent.Immutable;
import org.apache.commons.lang3.StringUtils;
import org.sonarsource.sonarlint.core.client.api.common.AbstractAnalysisConfiguration;

@Immutable
public class ConnectedAnalysisConfiguration extends AbstractAnalysisConfiguration {

  private final String projectKey;
  private final String toString;

  public ConnectedAnalysisConfiguration(Builder builder) {
    super(builder);
    this.projectKey = builder.projectKey;
    this.toString = generateString();
  }

  public static Builder builder() {
    return new Builder();
  }

  public String getProjectKey() {
    return projectKey;
  }

  @Override
  public String toString() {
    return toString;
  }

  private String generateString() {
    var sb = new StringBuilder();
    sb.append("[\n").append("  projectKey: ").append(projectKey).append("\n");
    generateToStringCommon(sb);
    generateToStringInputFiles(sb);
    sb.append("]\n");
    return sb.toString();
  }

  public static final class Builder extends AbstractBuilder<Builder> {
    private String projectKey;

    private Builder() {
    }

    public Builder setProjectKey(String projectKey) {
      this.projectKey = projectKey;
      return this;
    }

    public ConnectedAnalysisConfiguration build() {
      if (StringUtils.isBlank(projectKey)) {
        throw new IllegalStateException("'projectKey' is mandatory");
      }
      return new ConnectedAnalysisConfiguration(this);
    }
  }

}
