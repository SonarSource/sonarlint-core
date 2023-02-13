/*
 * SonarLint Core - Server API
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
package org.sonarsource.sonarlint.core.serverapi.hotspot;

import javax.annotation.CheckForNull;
import javax.annotation.Nullable;
import org.sonarsource.sonarlint.core.commons.TextRange;
import org.sonarsource.sonarlint.core.commons.VulnerabilityProbability;

public class ServerHotspotDetails {

  @Deprecated(forRemoval = true)
  public final String message;
  public final String filePath;
  @Deprecated(forRemoval = true)
  public final TextRange textRange;
  @Deprecated(forRemoval = true)
  public final String author;
  @Deprecated(forRemoval = true)
  public final Status status;
  @Deprecated(forRemoval = true)
  @CheckForNull
  public final Resolution resolution;
  @Deprecated(forRemoval = true)
  public final Rule rule;
  @Deprecated(forRemoval = true)
  @CheckForNull
  public final String codeSnippet;

  public ServerHotspotDetails(String message,
    String filePath,
    TextRange textRange,
    String author,
    Status status,
    @Nullable Resolution resolution,
    Rule rule,
    @Nullable String codeSnippet) {
    this.message = message;
    this.filePath = filePath;
    this.textRange = textRange;
    this.author = author;
    this.status = status;
    this.resolution = resolution;
    this.rule = rule;
    this.codeSnippet = codeSnippet;
  }

  @Deprecated(forRemoval = true)
  public static class Rule {

    public final String key;
    public final String name;
    public final String securityCategory;
    public final VulnerabilityProbability vulnerabilityProbability;
    public final String riskDescription;
    public final String vulnerabilityDescription;
    public final String fixRecommendations;

    public Rule(String key,
      String name,
      String securityCategory,
      VulnerabilityProbability vulnerabilityProbability,
      String riskDescription,
      String vulnerabilityDescription,
      String fixRecommendations) {

      this.key = key;
      this.name = name;
      this.securityCategory = securityCategory;
      this.vulnerabilityProbability = vulnerabilityProbability;
      this.riskDescription = riskDescription;
      this.vulnerabilityDescription = vulnerabilityDescription;
      this.fixRecommendations = fixRecommendations;
    }

  }

  @Deprecated(forRemoval = true)
  public enum Status {
    TO_REVIEW("To review"), REVIEWED("Reviewed");

    Status(String description) {
      this.description = description;
    }

    public final String description;
  }

  @Deprecated(forRemoval = true)
  public enum Resolution {
    FIXED("fixed"), SAFE("safe");

    Resolution(String description) {
      this.description = description;
    }

    public final String description;
  }
}
