/*
 * SonarLint Core - Server Connection
 * Copyright (C) 2016-2025 SonarSource Sàrl
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
package org.sonarsource.sonarlint.core.serverconnection.storage;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import org.jooq.JSON;
import org.sonarsource.sonarlint.core.commons.CleanCodeAttribute;
import org.sonarsource.sonarlint.core.commons.HotspotReviewStatus;
import org.sonarsource.sonarlint.core.commons.ImpactSeverity;
import org.sonarsource.sonarlint.core.commons.IssueSeverity;
import org.sonarsource.sonarlint.core.commons.IssueStatus;
import org.sonarsource.sonarlint.core.commons.RuleType;
import org.sonarsource.sonarlint.core.commons.SoftwareQuality;
import org.sonarsource.sonarlint.core.commons.VulnerabilityProbability;
import org.sonarsource.sonarlint.core.commons.api.SonarLanguage;
import org.sonarsource.sonarlint.core.commons.api.TextRange;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;
import org.sonarsource.sonarlint.core.commons.storage.model.tables.records.ServerDependencyRisksRecord;
import org.sonarsource.sonarlint.core.commons.storage.model.tables.records.ServerFindingsRecord;
import org.sonarsource.sonarlint.core.serverapi.hotspot.ServerHotspot;
import org.sonarsource.sonarlint.core.serverconnection.issues.FileLevelServerIssue;
import org.sonarsource.sonarlint.core.serverconnection.issues.LineLevelServerIssue;
import org.sonarsource.sonarlint.core.serverconnection.issues.RangeLevelServerIssue;
import org.sonarsource.sonarlint.core.serverconnection.issues.ServerDependencyRisk;
import org.sonarsource.sonarlint.core.serverconnection.issues.ServerIssue;
import org.sonarsource.sonarlint.core.serverconnection.issues.ServerTaintIssue;

public class EntityMapper {
  private static final SonarLintLogger LOG = SonarLintLogger.get();
  private final ObjectMapper objectMapper = new ObjectMapper();

  public JSON serializeImpacts(Map<SoftwareQuality, ImpactSeverity> impacts) {
    try {
      return JSON.valueOf(objectMapper.writeValueAsString(impacts));
    } catch (Exception e) {
      return JSON.valueOf("{}");
    }
  }

  public JSON serializeFlows(List<ServerTaintIssue.Flow> flows) {
    try {
      var flowsToSerialize = flows.stream().map(f -> new TaintFlow(f.locations().stream().map(l -> {
        var filePath = l.filePath();
        var textRangeWithHash = l.textRange();
        return new TaintLocation(filePath == null ? null : filePath.toString(),
          textRangeWithHash == null ? null
            : new TextRangeWithHash(textRangeWithHash.getStartLine(), textRangeWithHash.getStartLineOffset(), textRangeWithHash.getEndLine(), textRangeWithHash.getEndLineOffset(),
              textRangeWithHash.getHash()),
          l.message());
      }).toList())).toList();
      return JSON.valueOf(objectMapper.writeValueAsString(flowsToSerialize));
    } catch (Exception e) {
      return JSON.valueOf("[]");
    }
  }

  List<ServerTaintIssue.Flow> deserializeTaintFlows(JSON flows) {
    try {
      return objectMapper.readValue(flows.data(), new TypeReference<List<TaintFlow>>() {
      }).stream()
        .map(flow -> new ServerTaintIssue.Flow(flow.locations.stream()
          .map(l -> {
            var textRange = l.textRange;
            var filePath = l.filePath;
            return new ServerTaintIssue.ServerIssueLocation(filePath == null ? null : Path.of(filePath),
              textRange == null ? null
                : new org.sonarsource.sonarlint.core.commons.api.TextRangeWithHash(textRange.startLine, textRange.startLineOffset, textRange.endLine, textRange.endLineOffset,
                  textRange.hash),
              l.message);
          }).toList()))
        .toList();
    } catch (Exception e) {
      return List.of();
    }
  }

  // only needed to allow deserializing with Jackson
  record TaintFlow(List<TaintLocation> locations) {

  }

  record TaintLocation(@Nullable String filePath, @Nullable TextRangeWithHash textRange, @Nullable String message) {
  }

  record TextRangeWithHash(int startLine, int startLineOffset, int endLine, int endLineOffset, String hash) {
  }

  public JSON serializeTransitions(@Nullable List<ServerDependencyRisk.Transition> transitions) {
    if (transitions == null) {
      return null;
    }
    try {
      var stringList = transitions.stream().map(Enum::name).toList();
      return JSON.valueOf(objectMapper.writeValueAsString(stringList));
    } catch (Exception e) {
      LOG.error("Failed to serialize transitions {}", transitions, e);
      return JSON.valueOf("{}");
    }
  }

  public Map<SoftwareQuality, ImpactSeverity> deserializeImpacts(@Nullable JSON impactsJson) {
    if (impactsJson == null) {
      return Map.of();
    }
    try {
      var map = objectMapper.readValue(impactsJson.data(), new TypeReference<Map<String, String>>() {
      });
      return map.entrySet().stream()
        .collect(Collectors.toMap(entry -> SoftwareQuality.valueOf(entry.getKey()), entry -> ImpactSeverity.valueOf(entry.getValue())));
    } catch (Exception e) {
      LOG.error("Failed to deserialize impacts {}", impactsJson.data(), e);
      return Map.of();
    }
  }

  public List<ServerDependencyRisk.Transition> deserializeTransitions(@Nullable JSON json) {
    if (json == null) {
      return List.of();
    }
    try {
      var transitions = objectMapper.readValue(json.data(), new TypeReference<List<String>>() {
      });
      return transitions.stream()
        .map(transition -> {
          try {
            return ServerDependencyRisk.Transition.valueOf(transition);
          } catch (Exception e) {
            return null;
          }
        })
        .filter(Objects::nonNull)
        .toList();
    } catch (Exception e) {
      LOG.error("Failed to deserialize transitions {}", json.data(), e);
      return List.of();
    }
  }

  public Set<SonarLanguage> deserializeLanguages(@Nullable String[] languages) {
    if (languages == null) {
      return Set.of();
    }
    return Arrays.stream(languages).map(SonarLanguage::valueOf).collect(Collectors.toSet());
  }

  public String[] serializeLanguages(Set<SonarLanguage> enabledLanguages) {
    return enabledLanguages.stream().map(Enum::name).toList().toArray(new String[0]);
  }

  public ServerFindingsRecord serverIssueToRecord(ServerIssue<?> issue, String branchName, String connectionId, String sonarProjectKey) {
    var rec = new ServerFindingsRecord();
    // Server Issue fields
    rec.setId(issue.getId());
    rec.setServerKey(issue.getKey());
    rec.setResolved(issue.isResolved());
    var resolutionStatus = issue.getResolutionStatus();
    if (resolutionStatus != null) {
      rec.setIssueResolutionStatus(resolutionStatus.name());
    }
    rec.setRuleKey(issue.getRuleKey());
    rec.setMessage(issue.getMessage());
    rec.setFilePath(issue.getFilePath().toString());
    rec.setCreationDate(toLocalDateTime(issue.getCreationDate()));
    var userSeverity = issue.getUserSeverity();
    if (userSeverity != null) {
      rec.setUserSeverity(userSeverity.name());
    }
    rec.setRuleType(issue.getType().name());
    rec.setImpacts(serializeImpacts(issue.getImpacts()));
    // Range-Level Issue fields
    if (issue instanceof RangeLevelServerIssue rangeIssue) {
      rec.setStartLine(rangeIssue.getTextRange().getStartLine());
      rec.setStartLineOffset(rangeIssue.getTextRange().getStartLineOffset());
      rec.setEndLine(rangeIssue.getTextRange().getEndLine());
      rec.setEndLineOffset(rangeIssue.getTextRange().getEndLineOffset());
      rec.setTextRangeHash(rangeIssue.getTextRange().getHash());
    }
    // Line Level Issue fields
    if (issue instanceof LineLevelServerIssue lineIssue) {
      rec.setLine(lineIssue.getLine());
      rec.setLineHash(lineIssue.getLineHash());
    }
    // Record fields
    rec.setFindingType(ServerFindingType.ISSUE.name());
    rec.setBranchName(branchName);
    rec.setConnectionId(connectionId);
    rec.setSonarProjectKey(sonarProjectKey);
    return rec;
  }

  public ServerFindingsRecord serverHotspotToRecord(ServerHotspot hotspot, String branchName, String connectionId, String sonarProjectKey) {
    var rec = new ServerFindingsRecord();
    // Server Hotspot fields
    rec.setId(hotspot.getId());
    rec.setServerKey(hotspot.getKey());
    rec.setRuleKey(hotspot.getRuleKey());
    rec.setMessage(hotspot.getMessage());
    rec.setFilePath(hotspot.getFilePath().toString());
    // Text Range fields
    rec.setStartLine(hotspot.getTextRange().getStartLine());
    rec.setStartLineOffset(hotspot.getTextRange().getStartLineOffset());
    rec.setEndLine(hotspot.getTextRange().getEndLine());
    rec.setEndLineOffset(hotspot.getTextRange().getEndLineOffset());

    rec.setCreationDate(toLocalDateTime(hotspot.getCreationDate()));
    rec.setHotspotReviewStatus(hotspot.getStatus().name());
    rec.setVulnerabilityProbability(hotspot.getVulnerabilityProbability().name());
    rec.setAssignee(hotspot.getAssignee());
    // Record fields
    rec.setFindingType(ServerFindingType.HOTSPOT.name());
    rec.setBranchName(branchName);
    rec.setConnectionId(connectionId);
    rec.setSonarProjectKey(sonarProjectKey);
    return rec;
  }

  public ServerFindingsRecord serverTaintToRecord(ServerTaintIssue taint, String branchName, String connectionId, String sonarProjectKey) {
    var rec = new ServerFindingsRecord();
    // Server Taint fields
    rec.setId(taint.getId());
    rec.setServerKey(taint.getSonarServerKey());
    rec.setResolved(taint.isResolved());
    var resolutionStatus = taint.getResolutionStatus();
    if (resolutionStatus != null) {
      rec.setIssueResolutionStatus(resolutionStatus.name());
    }
    rec.setRuleKey(taint.getRuleKey());
    rec.setMessage(taint.getMessage());
    rec.setFilePath(taint.getFilePath().toString());
    rec.setCreationDate(toLocalDateTime(taint.getCreationDate()));
    rec.setUserSeverity(taint.getSeverity().name());
    rec.setRuleType(taint.getType().name());
    rec.setFlows(serializeFlows(taint.getFlows()));
    var textRange = taint.getTextRange();
    if (textRange != null) {
      rec.setStartLine(textRange.getStartLine());
      rec.setStartLineOffset(textRange.getStartLineOffset());
      rec.setEndLine(textRange.getEndLine());
      rec.setEndLineOffset(textRange.getEndLineOffset());
      rec.setTextRangeHash(textRange.getHash());
    }
    rec.setImpacts(serializeImpacts(taint.getImpacts()));
    rec.setRuleDescriptionContextKey(taint.getRuleDescriptionContextKey());
    taint.getCleanCodeAttribute()
      .ifPresent(codeAttribute -> rec.setCleanCodeAttribute(codeAttribute.name()));

    // Record fields
    rec.setFindingType(ServerFindingType.TAINT.name());
    rec.setBranchName(branchName);
    rec.setConnectionId(connectionId);
    rec.setSonarProjectKey(sonarProjectKey);
    return rec;
  }

  public ServerDependencyRisksRecord serverDependencyRiskToRecord(ServerDependencyRisk risk, String branchName, String connectionId, String sonarProjectKey) {
    var rec = new ServerDependencyRisksRecord();
    // Server Dependency Risk fields
    rec.setId(risk.key());
    rec.setType(risk.type().name());
    rec.setSeverity(risk.severity().name());
    rec.setSoftwareQuality(risk.quality().name());
    rec.setStatus(risk.status().name());
    rec.setPackageName(risk.packageName());
    rec.setPackageVersion(risk.packageVersion());
    rec.setVulnerabilityId(risk.vulnerabilityId());
    rec.setCvssScore(risk.cvssScore());
    rec.setTransitions(serializeTransitions(risk.transitions()));
    // Record fields
    rec.setBranchName(branchName);
    rec.setConnectionId(connectionId);
    rec.setSonarProjectKey(sonarProjectKey);
    return rec;
  }

  public ServerIssue<?> adaptIssue(ServerFindingsRecord rec) {
    var id = rec.getId();
    var serverKey = rec.getServerKey();
    var ruleKey = rec.getRuleKey();
    var message = rec.getMessage();
    var filePath = Path.of((rec.getFilePath()));
    var creationDate = toInstant(rec.getCreationDate());
    var userSeverity = rec.getUserSeverity() != null ? IssueSeverity.valueOf(rec.getUserSeverity()) : null;
    var type = rec.getRuleType() != null ? RuleType.valueOf(rec.getRuleType()) : RuleType.CODE_SMELL;
    var resolved = Boolean.TRUE.equals(rec.getResolved());
    var resolutionStatus = rec.getIssueResolutionStatus() != null ? IssueStatus.valueOf(rec.getIssueResolutionStatus()) : null;
    var impactsJson = rec.getImpacts();
    var impacts = deserializeImpacts(impactsJson);
    if (rec.getLine() != null) {
      return new LineLevelServerIssue(id, serverKey, resolved, resolutionStatus, ruleKey, message, rec.getLineHash(), filePath, creationDate, userSeverity, type,
        rec.getLine(), impacts);
    }
    if (rec.getStartLine() != null) {
      var textRangeWithHash = new org.sonarsource.sonarlint.core.commons.api.TextRangeWithHash(rec.getStartLine(), rec.getStartLineOffset(), rec.getEndLine(),
        rec.getEndLineOffset(), rec.getTextRangeHash());
      return new RangeLevelServerIssue(id, serverKey, resolved, resolutionStatus, ruleKey, message, filePath, creationDate, userSeverity, type, textRangeWithHash, impacts);
    }
    return new FileLevelServerIssue(id, serverKey, resolved, resolutionStatus, ruleKey, message, filePath, creationDate, userSeverity, type, impacts);
  }

  public ServerHotspot adaptHotspot(ServerFindingsRecord rec) {
    var id = rec.getId();
    var key = rec.getServerKey();
    var ruleKey = rec.getRuleKey();
    var message = rec.getMessage();
    var filePath = Path.of((rec.getFilePath()));
    var textRange = new TextRange(rec.getStartLine(), rec.getStartLineOffset(), rec.getEndLine(), rec.getEndLineOffset());
    var creationDate = toInstant(rec.getCreationDate());
    var status = HotspotReviewStatus.valueOf(rec.getHotspotReviewStatus());
    var prob = rec.getVulnerabilityProbability() != null ? VulnerabilityProbability.valueOf(rec.getVulnerabilityProbability()) : VulnerabilityProbability.MEDIUM;
    var assignee = rec.getAssignee();
    return new ServerHotspot(id, key, ruleKey, message, filePath, textRange, creationDate, status, prob, assignee);
  }

  public ServerTaintIssue adaptTaint(ServerFindingsRecord rec) {
    var id = rec.getId();
    var key = rec.getServerKey();
    var resolved = Boolean.TRUE.equals(rec.getResolved());
    var resolutionStatus = rec.getIssueResolutionStatus() != null ? IssueStatus.valueOf(rec.getIssueResolutionStatus()) : null;
    var ruleKey = rec.getRuleKey();
    var message = rec.getMessage();
    var filePath = Path.of(rec.getFilePath());
    var creationDate = toInstant(rec.getCreationDate());
    var severity = rec.getUserSeverity() != null ? IssueSeverity.valueOf(rec.getUserSeverity()) : IssueSeverity.MAJOR;
    var type = rec.getRuleType() != null ? RuleType.valueOf(rec.getRuleType()) : RuleType.CODE_SMELL;
    org.sonarsource.sonarlint.core.commons.api.TextRangeWithHash textRangeWithHash = null;
    if (rec.getStartLine() != null) {
      textRangeWithHash = new org.sonarsource.sonarlint.core.commons.api.TextRangeWithHash(rec.getStartLine(), rec.getStartLineOffset(), rec.getEndLine(), rec.getEndLineOffset(),
        rec.getTextRangeHash());
    }
    var ruleDescCtx = rec.getRuleDescriptionContextKey();
    var cleanCodeAttr = rec.getCleanCodeAttribute() != null ? CleanCodeAttribute.valueOf(rec.getCleanCodeAttribute()) : null;
    var impactsJson = rec.getImpacts();
    var impacts = deserializeImpacts(impactsJson);
    var flows = deserializeTaintFlows(rec.getFlows());
    return new ServerTaintIssue(id, key, resolved, resolutionStatus, ruleKey, message, filePath, creationDate,
      severity, type, textRangeWithHash, ruleDescCtx, cleanCodeAttr, impacts, flows);
  }

  private static Instant toInstant(LocalDateTime ldt) {
    return ldt.toInstant(ZoneOffset.UTC);
  }

  static LocalDateTime toLocalDateTime(Instant instant) {
    return LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
  }

}
