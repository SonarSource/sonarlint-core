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
package org.sonarsource.sonarlint.core.tracking.matching;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import org.sonarsource.sonarlint.core.analysis.RawIssue;
import org.sonarsource.sonarlint.core.commons.KnownFinding;
import org.sonarsource.sonarlint.core.tracking.IntroductionDateProvider;
import org.sonarsource.sonarlint.core.tracking.IssueMapper;
import org.sonarsource.sonarlint.core.tracking.KnownFindings;
import org.sonarsource.sonarlint.core.tracking.TextRangeUtils;
import org.sonarsource.sonarlint.core.tracking.TrackedIssue;

public class MatchingSession {
  private final Map<Path, List<KnownFinding>> remainingUnmatchedIssuesPerFile;
  private final Map<Path, List<KnownFinding>> remainingUnmatchedSecurityHotspotsPerFile;
  private final IntroductionDateProvider introductionDateProvider;
  private final ConcurrentHashMap<Path, List<TrackedIssue>> issuesPerFile = new ConcurrentHashMap<>();
  private final ConcurrentHashMap<Path, List<TrackedIssue>> securityHotspotsPerFile = new ConcurrentHashMap<>();
  private final Set<Path> relativePathsInvolved = new HashSet<>();

  public MatchingSession(KnownFindings previousFindings, IntroductionDateProvider introductionDateProvider) {
    this.remainingUnmatchedIssuesPerFile = previousFindings.getIssuesPerFile().entrySet().stream().map(entry -> Map.entry(entry.getKey(), new ArrayList<>(entry.getValue())))
      .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    this.remainingUnmatchedSecurityHotspotsPerFile = previousFindings.getSecurityHotspotsPerFile().entrySet().stream()
      .map(entry -> Map.entry(entry.getKey(), new ArrayList<>(entry.getValue()))).collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    this.introductionDateProvider = introductionDateProvider;
  }

  public TrackedIssue matchWithKnownFinding(Path relativePath, RawIssue rawIssue) {
    if (rawIssue.isSecurityHotspot()) {
      return matchWithKnownSecurityHotspot(relativePath, rawIssue);
    } else {
      return matchWithKnownIssue(relativePath, rawIssue);
    }
  }

  public TrackedIssue matchWithKnownSecurityHotspot(Path relativePath, RawIssue newSecurityHotspot) {
    var trackedSecurityHotspot = matchWithKnownFinding(relativePath, remainingUnmatchedSecurityHotspotsPerFile, newSecurityHotspot);
    securityHotspotsPerFile.computeIfAbsent(relativePath, f -> new ArrayList<>()).add(trackedSecurityHotspot);
    relativePathsInvolved.add(relativePath);
    return trackedSecurityHotspot;
  }

  private TrackedIssue matchWithKnownIssue(Path relativePath, RawIssue rawIssue) {
    var trackedIssue = matchWithKnownFinding(relativePath, remainingUnmatchedIssuesPerFile, rawIssue);
    issuesPerFile.computeIfAbsent(relativePath, f -> new ArrayList<>()).add(trackedIssue);
    relativePathsInvolved.add(relativePath);
    return trackedIssue;
  }

  private TrackedIssue matchWithKnownFinding(Path relativePath, Map<Path, List<KnownFinding>> remainingUnmatchedKnownFindingsPerFile, RawIssue newFinding) {
    var remainingUnmatchedKnownFindings = remainingUnmatchedKnownFindingsPerFile.getOrDefault(relativePath, new ArrayList<>());
    var localIssueMatcher = new IssueMatcher<>(new RawIssueFindingMatchingAttributeMapper(), new KnownIssueMatchingAttributesMapper());
    var localMatchingResult = localIssueMatcher.match(List.of(newFinding), remainingUnmatchedKnownFindings);
    return localMatchingResult.getMatchOpt(newFinding)
      .map(knownFinding -> {
        remainingUnmatchedKnownFindings.remove(knownFinding);
        return updateKnownFindingWithRawIssueData(knownFinding, newFinding);
      })
      .orElseGet(() -> newlyKnownIssue(relativePath, newFinding));
  }

  public static TrackedIssue updateKnownFindingWithRawIssueData(KnownFinding knownIssue, RawIssue rawIssue) {
    return new TrackedIssue(knownIssue.getId(), rawIssue.getMessage(), knownIssue.getIntroductionDate(),
      false, rawIssue.getSeverity(), rawIssue.getRuleType(), rawIssue.getRuleKey(),
      TextRangeUtils.getTextRangeWithHash(rawIssue.getTextRange(), rawIssue.getClientInputFile()),
      TextRangeUtils.getLineWithHash(rawIssue.getTextRange(), rawIssue.getClientInputFile()), knownIssue.getServerKey(), rawIssue.getImpacts(), rawIssue.getFlows(),
      rawIssue.getQuickFixes(), rawIssue.getVulnerabilityProbability(), null, rawIssue.getRuleDescriptionContextKey(),
      rawIssue.getCleanCodeAttribute(), rawIssue.getFileUri());
  }

  private TrackedIssue newlyKnownIssue(Path relativePath, RawIssue rawFinding) {
    var introductionDate = introductionDateProvider.determineIntroductionDate(relativePath, rawFinding.getLineNumbers());
    return IssueMapper.toTrackedIssue(rawFinding, introductionDate);
  }

  public Map<Path, List<TrackedIssue>> getIssuesPerFile() {
    return issuesPerFile;
  }

  public Map<Path, List<TrackedIssue>> getSecurityHotspotsPerFile() {
    return securityHotspotsPerFile;
  }

  public Set<Path> getRelativePathsInvolved() {
    return relativePathsInvolved;
  }
}
