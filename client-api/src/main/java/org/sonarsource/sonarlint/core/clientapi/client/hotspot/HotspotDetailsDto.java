/*
 * SonarLint Core - Client API
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
package org.sonarsource.sonarlint.core.clientapi.client.hotspot;

import javax.annotation.Nullable;

public class HotspotDetailsDto {
  private final String key;
  @Deprecated(forRemoval = true)
  private final String message;
  private final String filePath;
  @Deprecated(forRemoval = true)
  private final TextRangeDto textRange;
  @Deprecated(forRemoval = true)
  private final String author;
  @Deprecated(forRemoval = true)
  private final String status;
  @Deprecated(forRemoval = true)
  @Nullable
  private final String resolution;
  @Deprecated(forRemoval = true)
  private final HotspotRule rule;
  @Deprecated(forRemoval = true)
  @Nullable
  private final String codeSnippet;

  public HotspotDetailsDto(String key, String message, String filePath, TextRangeDto textRange, String author, String status, @Nullable String resolution, HotspotRule rule,
    @Nullable String codeSnippet) {
    this.key = key;
    this.message = message;
    this.filePath = filePath;
    this.textRange = textRange;
    this.author = author;
    this.status = status;
    this.resolution = resolution;
    this.rule = rule;
    this.codeSnippet = codeSnippet;
  }

  public String getKey() {
    return key;
  }

  public String getMessage() {
    return message;
  }

  public String getFilePath() {
    return filePath;
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

  public static class TextRangeDto {
    private final int startLine;
    private final int startLineOffset;
    private final int endLine;
    private final int endLineOffset;

    public TextRangeDto(int startLine, int startLineOffset, int endLine, int endLineOffset) {
      this.startLine = startLine;
      this.startLineOffset = startLineOffset;
      this.endLine = endLine;
      this.endLineOffset = endLineOffset;
    }

    public int getStartLine() {
      return startLine;
    }

    public int getStartLineOffset() {
      return startLineOffset;
    }

    public int getEndLine() {
      return endLine;
    }

    public int getEndLineOffset() {
      return endLineOffset;
    }
  }
}
