/*
 * SonarLint Core - RPC Protocol
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
package org.sonarsource.sonarlint.core.rpc.protocol.client.hotspot;

import java.nio.file.Path;
import javax.annotation.Nullable;
import org.sonarsource.sonarlint.core.rpc.protocol.common.TextRangeDto;

public class HotspotDetailsDto {
  private final String key;
  @Deprecated(forRemoval = true)
  private String message;
  private final Path ideFilePath;
  @Deprecated(forRemoval = true)
  private TextRangeDto textRange;
  @Deprecated(forRemoval = true)
  private String author;
  @Deprecated(forRemoval = true)
  private String status;
  @Deprecated(forRemoval = true)
  @Nullable
  private String resolution;
  @Deprecated(forRemoval = true)
  private HotspotRule rule;
  @Deprecated(forRemoval = true)
  @Nullable
  private String codeSnippet;

  public HotspotDetailsDto(String key, Path ideFilePath) {
    this.key = key;
    this.ideFilePath = ideFilePath;
  }

  /**
   * @deprecated use a more specific method instead
   */
  @Deprecated(forRemoval = true)
  public HotspotDetailsDto setMessage(String message) {
    this.message = message;
    return this;
  }

  /**
   * @deprecated use a more specific method instead
   */
  @Deprecated(forRemoval = true)
  public HotspotDetailsDto setTextRange(TextRangeDto textRange) {
    this.textRange = textRange;
    return this;
  }

  /**
   * @deprecated use a more specific method instead
   */
  @Deprecated(forRemoval = true)
  public HotspotDetailsDto setAuthor(String author) {
    this.author = author;
    return this;
  }

  /**
   * @deprecated use a more specific method instead
   */
  @Deprecated(forRemoval = true)
  public HotspotDetailsDto setStatus(String status) {
    this.status = status;
    return this;
  }

  /**
   * @deprecated use a more specific method instead
   */
  @Deprecated(forRemoval = true)
  public HotspotDetailsDto setResolution(@Nullable String resolution) {
    this.resolution = resolution;
    return this;
  }

  /**
   * @deprecated use a more specific method instead
   */
  @Deprecated(forRemoval = true)
  public HotspotDetailsDto setRule(HotspotRule rule) {
    this.rule = rule;
    return this;
  }

  /**
   * @deprecated use a more specific method instead
   */
  @Deprecated(forRemoval = true)
  public HotspotDetailsDto setCodeSnippet(@Nullable String codeSnippet) {
    this.codeSnippet = codeSnippet;
    return this;
  }

  public String getKey() {
    return key;
  }

  public String getMessage() {
    return message;
  }

  public Path getIdeFilePath() {
    return ideFilePath;
  }

  public TextRangeDto getTextRange() {
    return textRange;
  }

  public String getAuthor() {
    return author;
  }

  public String getStatus() {
    return status;
  }

  @Nullable
  public String getResolution() {
    return resolution;
  }

  public HotspotRule getRule() {
    return rule;
  }

  @Nullable
  public String getCodeSnippet() {
    return codeSnippet;
  }

  public static class HotspotRule {
    private final String key;
    private final String name;
    private final String securityCategory;
    private final String vulnerabilityProbability;
    private final String riskDescription;
    private final String vulnerabilityDescription;
    private final String fixRecommendations;

    public HotspotRule(String key, String name, String securityCategory, String vulnerabilityProbability, String riskDescription, String vulnerabilityDescription,
      String fixRecommendations) {
      this.key = key;
      this.name = name;
      this.securityCategory = securityCategory;
      this.vulnerabilityProbability = vulnerabilityProbability;
      this.riskDescription = riskDescription;
      this.vulnerabilityDescription = vulnerabilityDescription;
      this.fixRecommendations = fixRecommendations;
    }

    public String getKey() {
      return key;
    }

    public String getName() {
      return name;
    }

    public String getSecurityCategory() {
      return securityCategory;
    }

    public String getVulnerabilityProbability() {
      return vulnerabilityProbability;
    }

    public String getRiskDescription() {
      return riskDescription;
    }

    public String getVulnerabilityDescription() {
      return vulnerabilityDescription;
    }

    public String getFixRecommendations() {
      return fixRecommendations;
    }
  }
}
