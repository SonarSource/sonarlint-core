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
package org.sonarsource.sonarlint.core.rpc.protocol.backend.sca;

import java.util.List;
import javax.annotation.Nullable;

public class AnalyzeDependencyRiskProjectResponse {
  private final List<AnalyzeDependencyRiskProjectReleaseDto> releases;
  private final List<String> parsedFiles;
  private final List<AnalyzeDependencyRiskProjectErrorDto> errors;

  public AnalyzeDependencyRiskProjectResponse(List<AnalyzeDependencyRiskProjectReleaseDto> releases, List<String> parsedFiles,
    List<AnalyzeDependencyRiskProjectErrorDto> errors) {
    this.releases = releases;
    this.parsedFiles = parsedFiles;
    this.errors = errors;
  }

  public List<AnalyzeDependencyRiskProjectReleaseDto> getReleases() {
    return releases;
  }

  public List<String> getParsedFiles() {
    return parsedFiles;
  }

  public List<AnalyzeDependencyRiskProjectErrorDto> getErrors() {
    return errors;
  }

  public static class AnalyzeDependencyRiskProjectReleaseDto {
    private final String key;
    private final String packageUrl;
    private final String packageManager;
    private final String packageName;
    private final String version;
    @Nullable
    private final String licenseExpression;
    private final boolean known;
    private final boolean knownPackage;
    private final boolean newlyIntroduced;
    private final List<AnalyzeDependencyRiskProjectIssueDto> issues;
    private final List<String> dependencyFilePaths;
    private final List<List<String>> dependencyChains;

    public AnalyzeDependencyRiskProjectReleaseDto(String key, String packageUrl, String packageManager, String packageName, String version,
      @Nullable String licenseExpression, boolean known, boolean knownPackage, boolean newlyIntroduced, List<AnalyzeDependencyRiskProjectIssueDto> issues,
      List<String> dependencyFilePaths, List<List<String>> dependencyChains) {
      this.key = key;
      this.packageUrl = packageUrl;
      this.packageManager = packageManager;
      this.packageName = packageName;
      this.version = version;
      this.licenseExpression = licenseExpression;
      this.known = known;
      this.knownPackage = knownPackage;
      this.newlyIntroduced = newlyIntroduced;
      this.issues = issues;
      this.dependencyFilePaths = dependencyFilePaths;
      this.dependencyChains = dependencyChains;
    }

    public String getKey() {
      return key;
    }

    public String getPackageUrl() {
      return packageUrl;
    }

    public String getPackageManager() {
      return packageManager;
    }

    public String getPackageName() {
      return packageName;
    }

    public String getVersion() {
      return version;
    }

    @Nullable
    public String getLicenseExpression() {
      return licenseExpression;
    }

    public boolean isKnown() {
      return known;
    }

    public boolean isKnownPackage() {
      return knownPackage;
    }

    public boolean isNewlyIntroduced() {
      return newlyIntroduced;
    }

    public List<AnalyzeDependencyRiskProjectIssueDto> getIssues() {
      return issues;
    }

    public List<String> getDependencyFilePaths() {
      return dependencyFilePaths;
    }

    public List<List<String>> getDependencyChains() {
      return dependencyChains;
    }
  }

  public static class AnalyzeDependencyRiskProjectIssueDto {
    @Nullable
    private final String key;
    private final String severity;
    @Nullable
    private final Boolean showIncreasedSeverityWarning;
    private final String type;
    private final String quality;
    @Nullable
    private final String status;
    @Nullable
    private final String vulnerabilityId;
    @Nullable
    private final List<String> cweIds;
    @Nullable
    private final String cvssScore;
    @Nullable
    private final String spdxLicenseId;
    @Nullable
    private final List<AnalyzeDependencyRiskProjectVersionOptionDto> versionOptions;

    public AnalyzeDependencyRiskProjectIssueDto(@Nullable String key, String severity, @Nullable Boolean showIncreasedSeverityWarning, String type, String quality,
      @Nullable String status, @Nullable String vulnerabilityId, @Nullable List<String> cweIds, @Nullable String cvssScore, @Nullable String spdxLicenseId,
      @Nullable List<AnalyzeDependencyRiskProjectVersionOptionDto> versionOptions) {
      this.key = key;
      this.severity = severity;
      this.showIncreasedSeverityWarning = showIncreasedSeverityWarning;
      this.type = type;
      this.quality = quality;
      this.status = status;
      this.vulnerabilityId = vulnerabilityId;
      this.cweIds = cweIds;
      this.cvssScore = cvssScore;
      this.spdxLicenseId = spdxLicenseId;
      this.versionOptions = versionOptions;
    }

    @Nullable
    public String getKey() {
      return key;
    }

    public String getSeverity() {
      return severity;
    }

    @Nullable
    public Boolean getShowIncreasedSeverityWarning() {
      return showIncreasedSeverityWarning;
    }

    public String getType() {
      return type;
    }

    public String getQuality() {
      return quality;
    }

    @Nullable
    public String getStatus() {
      return status;
    }

    @Nullable
    public String getVulnerabilityId() {
      return vulnerabilityId;
    }

    @Nullable
    public List<String> getCweIds() {
      return cweIds;
    }

    @Nullable
    public String getCvssScore() {
      return cvssScore;
    }

    @Nullable
    public String getSpdxLicenseId() {
      return spdxLicenseId;
    }

    @Nullable
    public List<AnalyzeDependencyRiskProjectVersionOptionDto> getVersionOptions() {
      return versionOptions;
    }
  }

  public static class AnalyzeDependencyRiskProjectVersionOptionDto {
    private final String version;
    private final List<String> vulnerabilityIds;
    private final boolean prerelease;
    private final String fixLevel;
    private final String descriptionCode;

    public AnalyzeDependencyRiskProjectVersionOptionDto(String version, List<String> vulnerabilityIds, boolean prerelease, String fixLevel, String descriptionCode) {
      this.version = version;
      this.vulnerabilityIds = vulnerabilityIds;
      this.prerelease = prerelease;
      this.fixLevel = fixLevel;
      this.descriptionCode = descriptionCode;
    }

    public String getVersion() {
      return version;
    }

    public List<String> getVulnerabilityIds() {
      return vulnerabilityIds;
    }

    public boolean isPrerelease() {
      return prerelease;
    }

    public String getFixLevel() {
      return fixLevel;
    }

    public String getDescriptionCode() {
      return descriptionCode;
    }
  }

  public static class AnalyzeDependencyRiskProjectErrorDto {
    private final String id;
    private final String code;
    @Nullable
    private final String path;
    private final String message;

    public AnalyzeDependencyRiskProjectErrorDto(String id, String code, @Nullable String path, String message) {
      this.id = id;
      this.code = code;
      this.path = path;
      this.message = message;
    }

    public String getId() {
      return id;
    }

    public String getCode() {
      return code;
    }

    @Nullable
    public String getPath() {
      return path;
    }

    public String getMessage() {
      return message;
    }
  }
}
