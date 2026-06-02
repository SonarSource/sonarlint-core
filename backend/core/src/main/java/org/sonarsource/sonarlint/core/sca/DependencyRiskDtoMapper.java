/*
 * SonarLint Core - Implementation
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
package org.sonarsource.sonarlint.core.sca;

import com.sonar.sca.scanner.analyzeproject.response.AnalysisErrorResource;
import com.sonar.sca.scanner.analyzeproject.response.AnalyzeProjectIssue;
import com.sonar.sca.scanner.analyzeproject.response.AnalyzeProjectRelease;
import com.sonar.sca.scanner.analyzeproject.response.Cwe;
import com.sonar.sca.scanner.analyzeproject.response.VersionOption;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import javax.annotation.Nullable;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.sca.AnalyzeDependencyRiskProjectResponse.AnalyzeDependencyRiskProjectErrorDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.tracking.DependencyRiskDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.tracking.DependencyRiskDto.DependencyDetailsDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.tracking.DependencyRiskDto.IssueDetailsDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.tracking.DependencyRiskDto.LocalAnalysisDetailsDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.tracking.DependencyRiskDto.ReleaseDetailsDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.tracking.DependencyRiskDto.VersionOptionDto;


public class DependencyRiskDtoMapper {
  private static final SonarLintLogger LOG = SonarLintLogger.get();

  /**
   * Builds a local-only {@link DependencyRiskDto} for a freshly detected scanner issue.
   * <p>
   * Local-only issues are not matched to a server dependency risk. The scanner issue key can be missing or invalid; in
   * that case the DTO id remains {@code null}, while {@link DependencyRiskDto.Presence#LOCAL_ONLY} makes the source explicit.
   */
  public Optional<DependencyRiskDto> toLocalOnlyDto(AnalyzeProjectRelease release, AnalyzeProjectIssue issue) {
    var id = parseUuidOrNull(issue.key());
    if (id == null) {
      LOG.debug("Keeping local-only SCA issue without server UUID (package={}, vulnerability={})", release.packageName(), issue.vulnerabilityId());
    }
    var serverShape = DependencyRiskDto.builder()
      .id(id)
      .type(toType(issue.type().name()))
      .severity(toSeverity(issue.severity()))
      .quality(toQuality(issue.quality().name()))
      .status(toStatus(issue.status()))
      .packageName(release.packageName())
      .packageVersion(release.version())
      .vulnerabilityId(issue.vulnerabilityId())
      .cvssScore(issue.cvssScore())
      .transitions(Collections.emptyList())
      .build();
    return Optional.of(DependencyRiskDto.withLocalAnalysis(serverShape, buildLocalDetails(release, issue), DependencyRiskDto.Presence.LOCAL_ONLY));
  }

  /**
   * Returns a new {@link DependencyRiskDto} that combines the server-tracked DTO with the local-analysis enrichment for
   * the matched release/issue pair. Server fields (status, transitions, severity, …) are preserved as the source of
   * truth; local fields are attached as {@link LocalAnalysisDetailsDto}.
   */
  public DependencyRiskDto enrichServerDto(DependencyRiskDto serverDto, AnalyzeProjectRelease release, AnalyzeProjectIssue issue) {
    return DependencyRiskDto.withLocalAnalysis(serverDto, buildLocalDetails(release, issue), DependencyRiskDto.Presence.SERVER_AND_LOCAL);
  }

  public AnalyzeDependencyRiskProjectErrorDto toErrorDto(AnalysisErrorResource error) {
    return new AnalyzeDependencyRiskProjectErrorDto(error.id(), error.code().name(), error.path(), error.message());
  }

  private static LocalAnalysisDetailsDto buildLocalDetails(AnalyzeProjectRelease release, AnalyzeProjectIssue issue) {
    var releaseDetails = new ReleaseDetailsDto(release.key(), release.packageUrl(), release.packageManager(),
      release.licenseExpression(), release.known(), release.knownPackage(), release.newlyIntroduced());
    var issueDetails = new IssueDetailsDto(issue.showIncreasedSeverityWarning(), toCweIds(issue), issue.spdxLicenseId(),
      toVersionOptionDtos(issue.versionOptions()));
    var dependencyDetails = new DependencyDetailsDto(release.dependencyFilePaths(), release.dependencyChains());
    return new LocalAnalysisDetailsDto(releaseDetails, issueDetails, dependencyDetails);
  }

  private static List<String> toCweIds(AnalyzeProjectIssue issue) {
    return issue.cwes().stream().map(Cwe::code).toList();
  }

  @Nullable
  private static List<VersionOptionDto> toVersionOptionDtos(@Nullable List<VersionOption> versionOptions) {
    if (versionOptions == null) {
      return null;
    }
    return versionOptions.stream().map(DependencyRiskDtoMapper::toVersionOptionDto).toList();
  }

  private static VersionOptionDto toVersionOptionDto(VersionOption versionOption) {
    return new VersionOptionDto(versionOption.version(), versionOption.vulnerabilityIds(), versionOption.prerelease(),
      versionOption.fixLevel(), versionOption.descriptionCode());
  }

  @Nullable
  private static UUID parseUuidOrNull(@Nullable String key) {
    if (key == null || key.isBlank()) {
      return null;
    }
    try {
      return UUID.fromString(key);
    } catch (IllegalArgumentException e) {
      return null;
    }
  }

  private static DependencyRiskDto.Type toType(String name) {
    try {
      return DependencyRiskDto.Type.valueOf(name);
    } catch (IllegalArgumentException e) {
      LOG.debug("Unknown SCA issue type '{}', falling back to VULNERABILITY", name);
      return DependencyRiskDto.Type.VULNERABILITY;
    }
  }

  private static DependencyRiskDto.Severity toSeverity(@Nullable String severity) {
    if (severity == null) {
      return DependencyRiskDto.Severity.INFO;
    }
    try {
      return DependencyRiskDto.Severity.valueOf(severity);
    } catch (IllegalArgumentException e) {
      LOG.debug("Unknown SCA severity '{}', falling back to INFO", severity);
      return DependencyRiskDto.Severity.INFO;
    }
  }

  private static DependencyRiskDto.SoftwareQuality toQuality(String name) {
    try {
      return DependencyRiskDto.SoftwareQuality.valueOf(name);
    } catch (IllegalArgumentException e) {
      LOG.debug("Unknown SCA software quality '{}', falling back to SECURITY", name);
      return DependencyRiskDto.SoftwareQuality.SECURITY;
    }
  }

  private static DependencyRiskDto.Status toStatus(@Nullable String status) {
    if (status == null || status.isBlank()) {
      return DependencyRiskDto.Status.OPEN;
    }
    try {
      return DependencyRiskDto.Status.valueOf(status);
    } catch (IllegalArgumentException e) {
      LOG.debug("Unknown SCA status '{}', falling back to OPEN", status);
      return DependencyRiskDto.Status.OPEN;
    }
  }
}



