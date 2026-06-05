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
package org.sonarsource.sonarlint.core.rpc.protocol.backend.tracking;

import java.util.List;
import java.util.UUID;
import javax.annotation.CheckForNull;
import javax.annotation.Nullable;

public class DependencyRiskDto {
  private final UUID id;
  private final Type type;
  private final Severity severity;
  private final SoftwareQuality quality;
  private final Status status;
  private final String packageName;
  private final String packageVersion;
  @Nullable
  private final String vulnerabilityId;
  @Nullable
  private final String cvssScore;
  private final List<Transition> transitions;
  @Nullable
  private final LocalAnalysisDetailsDto localAnalysisDetails;
  private final Presence presence;

  private DependencyRiskDto(Builder builder) {
    this.id = builder.id == null ? UUID.randomUUID() : builder.id;
    this.type = builder.type;
    this.severity = builder.severity;
    this.quality = builder.quality;
    this.status = builder.status;
    this.packageName = builder.packageName;
    this.packageVersion = builder.packageVersion;
    this.vulnerabilityId = builder.vulnerabilityId;
    this.cvssScore = builder.cvssScore;
    this.transitions = builder.transitions;
    this.localAnalysisDetails = builder.localAnalysisDetails;
    this.presence = builder.presence;
  }

  public static Builder builder() {
    return new Builder();
  }

  public static DependencyRiskDto withLocalAnalysis(DependencyRiskDto base, LocalAnalysisDetailsDto localAnalysisDetails, Presence presence) {
    return base.toBuilder()
      .localAnalysisDetails(localAnalysisDetails)
      .presence(presence)
      .build();
  }

  private Builder toBuilder() {
    return new Builder()
      .id(id)
      .type(type)
      .severity(severity)
      .quality(quality)
      .status(status)
      .packageName(packageName)
      .packageVersion(packageVersion)
      .vulnerabilityId(vulnerabilityId)
      .cvssScore(cvssScore)
      .transitions(transitions)
      .localAnalysisDetails(localAnalysisDetails)
      .presence(presence);
  }

  public static class Builder {
    @Nullable
    private UUID id;
    private Type type;
    private Severity severity;
    private SoftwareQuality quality;
    private Status status;
    private String packageName;
    private String packageVersion;
    @Nullable
    private String vulnerabilityId;
    @Nullable
    private String cvssScore;
    private List<Transition> transitions = List.of();
    @Nullable
    private LocalAnalysisDetailsDto localAnalysisDetails;
    private Presence presence = Presence.SERVER_ONLY;

    public Builder id(@Nullable UUID id) {
      this.id = id;
      return this;
    }

    public Builder type(Type type) {
      this.type = type;
      return this;
    }

    public Builder severity(Severity severity) {
      this.severity = severity;
      return this;
    }

    public Builder quality(SoftwareQuality quality) {
      this.quality = quality;
      return this;
    }

    public Builder status(Status status) {
      this.status = status;
      return this;
    }

    public Builder packageName(String packageName) {
      this.packageName = packageName;
      return this;
    }

    public Builder packageVersion(String packageVersion) {
      this.packageVersion = packageVersion;
      return this;
    }

    public Builder vulnerabilityId(@Nullable String vulnerabilityId) {
      this.vulnerabilityId = vulnerabilityId;
      return this;
    }

    public Builder cvssScore(@Nullable String cvssScore) {
      this.cvssScore = cvssScore;
      return this;
    }

    public Builder transitions(List<Transition> transitions) {
      this.transitions = transitions;
      return this;
    }

    public Builder localAnalysisDetails(@Nullable LocalAnalysisDetailsDto localAnalysisDetails) {
      this.localAnalysisDetails = localAnalysisDetails;
      return this;
    }

    public Builder presence(Presence presence) {
      this.presence = presence;
      return this;
    }

    public DependencyRiskDto build() {
      return new DependencyRiskDto(this);
    }
  }

  public UUID getId() {
    return id;
  }

  public Type getType() {
    return type;
  }

  public Severity getSeverity() {
    return severity;
  }

  public SoftwareQuality getQuality() {
    return quality;
  }

  public Status getStatus() {
    return status;
  }

  public String getPackageName() {
    return packageName;
  }

  public String getPackageVersion() {
    return packageVersion;
  }

  @CheckForNull
  public String getVulnerabilityId() {
    return vulnerabilityId;
  }

  @CheckForNull
  public String getCvssScore() {
    return cvssScore;
  }

  public List<Transition> getTransitions() {
    return transitions;
  }

  @CheckForNull
  public LocalAnalysisDetailsDto getLocalAnalysisDetails() {
    return localAnalysisDetails;
  }

  public Presence getPresence() {
    return presence;
  }

  public static class LocalAnalysisDetailsDto {
    private final ReleaseDetailsDto releaseDetails;
    private final IssueDetailsDto issueDetails;
    private final DependencyDetailsDto dependencyDetails;

    public LocalAnalysisDetailsDto(ReleaseDetailsDto releaseDetails, IssueDetailsDto issueDetails, DependencyDetailsDto dependencyDetails) {
      this.releaseDetails = releaseDetails;
      this.issueDetails = issueDetails;
      this.dependencyDetails = dependencyDetails;
    }

    public ReleaseDetailsDto getReleaseDetails() {
      return releaseDetails;
    }

    public IssueDetailsDto getIssueDetails() {
      return issueDetails;
    }

    public DependencyDetailsDto getDependencyDetails() {
      return dependencyDetails;
    }
  }

  public static class ReleaseDetailsDto {
    private final String releaseKey;
    private final String packageUrl;
    private final String packageManager;
    @Nullable
    private final String licenseExpression;
    private final boolean known;
    private final boolean knownPackage;
    private final boolean newlyIntroduced;

    public ReleaseDetailsDto(String releaseKey, String packageUrl, String packageManager, @Nullable String licenseExpression, boolean known,
      boolean knownPackage, boolean newlyIntroduced) {
      this.releaseKey = releaseKey;
      this.packageUrl = packageUrl;
      this.packageManager = packageManager;
      this.licenseExpression = licenseExpression;
      this.known = known;
      this.knownPackage = knownPackage;
      this.newlyIntroduced = newlyIntroduced;
    }

    public String getReleaseKey() {
      return releaseKey;
    }

    public String getPackageUrl() {
      return packageUrl;
    }

    public String getPackageManager() {
      return packageManager;
    }

    @CheckForNull
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
  }

  public static class IssueDetailsDto {
    @Nullable
    private final Boolean showIncreasedSeverityWarning;
    @Nullable
    private final List<String> cweIds;
    @Nullable
    private final String spdxLicenseId;
    @Nullable
    private final List<VersionOptionDto> versionOptions;

    public IssueDetailsDto(@Nullable Boolean showIncreasedSeverityWarning, @Nullable List<String> cweIds, @Nullable String spdxLicenseId,
      @Nullable List<VersionOptionDto> versionOptions) {
      this.showIncreasedSeverityWarning = showIncreasedSeverityWarning;
      this.cweIds = cweIds;
      this.spdxLicenseId = spdxLicenseId;
      this.versionOptions = versionOptions;
    }

    @CheckForNull
    public Boolean getShowIncreasedSeverityWarning() {
      return showIncreasedSeverityWarning;
    }

    @CheckForNull
    public List<String> getCweIds() {
      return cweIds;
    }

    @CheckForNull
    public String getSpdxLicenseId() {
      return spdxLicenseId;
    }

    @CheckForNull
    public List<VersionOptionDto> getVersionOptions() {
      return versionOptions;
    }
  }

  public static class DependencyDetailsDto {
    private final List<String> dependencyFilePaths;
    private final List<List<String>> dependencyChains;

    public DependencyDetailsDto(List<String> dependencyFilePaths, List<List<String>> dependencyChains) {
      this.dependencyFilePaths = dependencyFilePaths;
      this.dependencyChains = dependencyChains;
    }

    public List<String> getDependencyFilePaths() {
      return dependencyFilePaths;
    }

    public List<List<String>> getDependencyChains() {
      return dependencyChains;
    }
  }

  public static class VersionOptionDto {
    private final String version;
    private final List<String> vulnerabilityIds;
    private final boolean prerelease;
    private final String fixLevel;
    private final String descriptionCode;

    public VersionOptionDto(String version, List<String> vulnerabilityIds, boolean prerelease, String fixLevel, String descriptionCode) {
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

  public enum Severity {
    INFO, LOW, MEDIUM, HIGH, BLOCKER
  }

  public enum SoftwareQuality {
    MAINTAINABILITY,
    RELIABILITY,
    SECURITY
  }

  public enum Type {
    VULNERABILITY, PROHIBITED_LICENSE, MALWARE
  }

  public enum Status {
    FIXED, OPEN, CONFIRM, ACCEPT, SAFE
  }

  public enum Transition {
    CONFIRM, REOPEN, SAFE, FIXED, ACCEPT
  }

  public enum Presence {
    SERVER_ONLY, LOCAL_ONLY, SERVER_AND_LOCAL
  }
}
