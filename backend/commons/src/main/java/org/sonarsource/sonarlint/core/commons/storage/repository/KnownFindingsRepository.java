/*
 * SonarLint Core - Commons
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
package org.sonarsource.sonarlint.core.commons.storage.repository;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.jooq.Configuration;
import org.jooq.Record;
import org.sonarsource.sonarlint.core.commons.KnownFinding;
import org.sonarsource.sonarlint.core.commons.KnownFindingType;
import org.sonarsource.sonarlint.core.commons.LineWithHash;
import org.sonarsource.sonarlint.core.commons.api.TextRangeWithHash;
import org.sonarsource.sonarlint.core.commons.storage.SonarLintDatabase;
import org.sonarsource.sonarlint.core.commons.storage.model.Tables;
import org.sonarsource.sonarlint.core.commons.storage.model.tables.records.KnownFindingsRecord;

import static org.sonarsource.sonarlint.core.commons.storage.model.Tables.KNOWN_FINDINGS;

public class KnownFindingsRepository {

  private final SonarLintDatabase database;

  public KnownFindingsRepository(SonarLintDatabase database) {
    this.database = database;
  }

  public void storeFindings(Map<String, Map<Path, Findings>> findingsPerFilePerConfigScopeId) {
    var records = findingsPerFilePerConfigScopeId.entrySet().stream()
      .flatMap(KnownFindingsRepository::expandConfigScope)
      .toList();
    database.dsl().deleteFrom(Tables.KNOWN_FINDINGS).execute();
    database.dsl().batchInsert(records).execute();
  }

  private static Stream<KnownFindingsRecord> expandConfigScope(Map.Entry<String, Map<Path, Findings>> configScopeEntry) {
    var configScopeId = configScopeEntry.getKey();
    return configScopeEntry.getValue().entrySet().stream()
      .flatMap(fileEntry -> expandFileFindings(configScopeId, fileEntry));
  }

  private static Stream<KnownFindingsRecord> expandFileFindings(String configScopeId, Map.Entry<Path, Findings> fileEntry) {
    var filePath = fileEntry.getKey();
    var findings = fileEntry.getValue();

    return Stream.concat(
      findings.issues().stream()
        .map(f -> createRecord(f, configScopeId, filePath, KnownFindingType.ISSUE)),
      findings.hotspots().stream()
        .map(f -> createRecord(f, configScopeId, filePath, KnownFindingType.HOTSPOT))
    );
  }

  private static KnownFindingsRecord createRecord(KnownFinding finding, String configScopeId, Path filePath, KnownFindingType type) {
    var textRangeWithHash = finding.getTextRangeWithHash();
    var lineWithHash = finding.getLineWithHash();
    var introductionDate = LocalDateTime.ofInstant(finding.getIntroductionDate(), ZoneOffset.UTC);
    return new KnownFindingsRecord(
      finding.getId(),
      configScopeId,
      filePath.toString(),
      finding.getServerKey(),
      finding.getRuleKey(),
      finding.getMessage(),
      introductionDate,
      type.name(),
      textRangeWithHash == null ? null : textRangeWithHash.getStartLine(),
      textRangeWithHash == null ? null : textRangeWithHash.getStartLineOffset(),
      textRangeWithHash == null ? null : textRangeWithHash.getEndLine(),
      textRangeWithHash == null ? null : textRangeWithHash.getEndLineOffset(),
      textRangeWithHash == null ? null : textRangeWithHash.getHash(),
      lineWithHash == null ? null : lineWithHash.getNumber(),
      lineWithHash == null ? null : lineWithHash.getHash());
  }

  public void storeKnownIssues(String configurationScopeId, Path clientRelativePath, List<KnownFinding> newKnownIssues) {
    storeKnownFindings(configurationScopeId, clientRelativePath, newKnownIssues, KnownFindingType.ISSUE);
  }

  public void storeKnownSecurityHotspots(String configurationScopeId, Path clientRelativePath, List<KnownFinding> newKnownSecurityHotspots) {
    storeKnownFindings(configurationScopeId, clientRelativePath, newKnownSecurityHotspots, KnownFindingType.HOTSPOT);
  }

  public List<KnownFinding> loadSecurityHotspotsForFile(String configurationScopeId, Path filePath) {
    return getKnownFindingsForFile(configurationScopeId, filePath, KnownFindingType.HOTSPOT);
  }

  public List<KnownFinding> loadIssuesForFile(String configurationScopeId, Path filePath) {
    return getKnownFindingsForFile(configurationScopeId, filePath, KnownFindingType.ISSUE);
  }

  private void storeKnownFindings(String configurationScopeId, Path clientRelativePath, List<KnownFinding> newKnownFindings, KnownFindingType type) {
    database.dsl().transaction((Configuration trx) -> newKnownFindings.forEach(finding -> {
      var textRangeWithHash = finding.getTextRangeWithHash();
      var startLine = textRangeWithHash == null ? null : textRangeWithHash.getStartLine();
      var startLineOffset = textRangeWithHash == null ? null : textRangeWithHash.getStartLineOffset();
      var endLine = textRangeWithHash == null ? null : textRangeWithHash.getEndLine();
      var endLineOffset = textRangeWithHash == null ? null : textRangeWithHash.getEndLineOffset();
      var textRangeHash = textRangeWithHash == null ? null : textRangeWithHash.getHash();

      var lineWithHash = finding.getLineWithHash();
      var line = lineWithHash == null ? null : lineWithHash.getNumber();
      var lineHash = lineWithHash == null ? null : lineWithHash.getHash();
      var introDate = LocalDateTime.ofInstant(finding.getIntroductionDate(), ZoneOffset.UTC);
      trx.dsl().mergeInto(KNOWN_FINDINGS)
        .using(trx.dsl().selectOne())
        .on(KNOWN_FINDINGS.ID.eq(finding.getId()))
        .whenMatchedThenUpdate()
        .set(KNOWN_FINDINGS.CONFIGURATION_SCOPE_ID, configurationScopeId)
        .set(KNOWN_FINDINGS.IDE_RELATIVE_FILE_PATH, clientRelativePath.toString())
        .set(KNOWN_FINDINGS.SERVER_KEY, finding.getServerKey())
        .set(KNOWN_FINDINGS.RULE_KEY, finding.getRuleKey())
        .set(KNOWN_FINDINGS.MESSAGE, finding.getMessage())
        .set(KNOWN_FINDINGS.INTRODUCTION_DATE, introDate)
        .set(KNOWN_FINDINGS.FINDING_TYPE, type.name())
        .set(KNOWN_FINDINGS.START_LINE, startLine)
        .set(KNOWN_FINDINGS.START_LINE_OFFSET, startLineOffset)
        .set(KNOWN_FINDINGS.END_LINE, endLine)
        .set(KNOWN_FINDINGS.END_LINE_OFFSET, endLineOffset)
        .set(KNOWN_FINDINGS.TEXT_RANGE_HASH, textRangeHash)
        .set(KNOWN_FINDINGS.LINE, line)
        .set(KNOWN_FINDINGS.LINE_HASH, lineHash)
        .whenNotMatchedThenInsert(KNOWN_FINDINGS.ID, KNOWN_FINDINGS.CONFIGURATION_SCOPE_ID, KNOWN_FINDINGS.IDE_RELATIVE_FILE_PATH, KNOWN_FINDINGS.SERVER_KEY,
          KNOWN_FINDINGS.RULE_KEY, KNOWN_FINDINGS.MESSAGE, KNOWN_FINDINGS.INTRODUCTION_DATE, KNOWN_FINDINGS.FINDING_TYPE,
          KNOWN_FINDINGS.START_LINE, KNOWN_FINDINGS.START_LINE_OFFSET, KNOWN_FINDINGS.END_LINE, KNOWN_FINDINGS.END_LINE_OFFSET, KNOWN_FINDINGS.TEXT_RANGE_HASH,
          KNOWN_FINDINGS.LINE, KNOWN_FINDINGS.LINE_HASH)
        .values(finding.getId(), configurationScopeId, clientRelativePath.toString(), finding.getServerKey(), finding.getRuleKey(),
          finding.getMessage(), introDate, type.name(),
          startLine, startLineOffset, endLine, endLineOffset, textRangeHash,
          line, lineHash)
        .execute();
    }));
  }

  private List<KnownFinding> getKnownFindingsForFile(String configurationScopeId, Path filePath, KnownFindingType type) {
    var issuesInFile = database.dsl()
      .selectFrom(KNOWN_FINDINGS)
      .where(KNOWN_FINDINGS.CONFIGURATION_SCOPE_ID.eq(configurationScopeId)
        .and(KNOWN_FINDINGS.IDE_RELATIVE_FILE_PATH.eq(filePath.toString()))
        .and(KNOWN_FINDINGS.FINDING_TYPE.eq(type.name())))
      .fetch();
    return issuesInFile.stream()
      .map(KnownFindingsRepository::recordToKnownFinding)
      .toList();
  }

  private static KnownFinding recordToKnownFinding(Record rec) {
    var id = rec.get(KNOWN_FINDINGS.ID);
    var introductionDate = rec.get(KNOWN_FINDINGS.INTRODUCTION_DATE).toInstant(ZoneOffset.UTC);
    var textRangeWithHash = getTextRangeWithHash(rec);
    var lineWithHash = getLineWithHash(rec);
    return new KnownFinding(
      id,
      rec.get(KNOWN_FINDINGS.SERVER_KEY),
      textRangeWithHash, lineWithHash,
      rec.get(KNOWN_FINDINGS.RULE_KEY),
      rec.get(KNOWN_FINDINGS.MESSAGE),
      introductionDate);
  }

  private static LineWithHash getLineWithHash(Record rec) {
    var line = rec.get(KNOWN_FINDINGS.LINE);
    if (line == null) {
      return null;
    }
    var hash = rec.get(KNOWN_FINDINGS.LINE_HASH);
    return new LineWithHash(line, hash);
  }

  private static TextRangeWithHash getTextRangeWithHash(Record rec) {
    var startLine = rec.get(KNOWN_FINDINGS.START_LINE);
    if (startLine == null) {
      return null;
    }
    var endLine = rec.get(KNOWN_FINDINGS.END_LINE);
    var startLineOffset = rec.get(KNOWN_FINDINGS.START_LINE_OFFSET);
    var endLineOffset = rec.get(KNOWN_FINDINGS.END_LINE_OFFSET);
    var hash = rec.get(KNOWN_FINDINGS.TEXT_RANGE_HASH);
    return new TextRangeWithHash(startLine, startLineOffset, endLine, endLineOffset, hash);
  }
}
