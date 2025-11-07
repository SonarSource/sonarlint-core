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
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.jooq.Configuration;
import org.jooq.Record;
import org.sonarsource.sonarlint.core.commons.IssueStatus;
import org.sonarsource.sonarlint.core.commons.LineWithHash;
import org.sonarsource.sonarlint.core.commons.LocalOnlyIssue;
import org.sonarsource.sonarlint.core.commons.LocalOnlyIssueResolution;
import org.sonarsource.sonarlint.core.commons.api.TextRangeWithHash;
import org.sonarsource.sonarlint.core.commons.storage.SonarLintDatabase;

import static org.sonarsource.sonarlint.core.commons.storage.model.Tables.LOCAL_ONLY_ISSUES;

public class LocalOnlyIssuesRepository {

  private final SonarLintDatabase database;

  public LocalOnlyIssuesRepository(SonarLintDatabase database) {
    this.database = database;
  }

  public List<LocalOnlyIssue> loadForFile(String configurationScopeId, Path filePath) {
    var issuesInFile = database.dsl()
      .selectFrom(LOCAL_ONLY_ISSUES)
      .where(LOCAL_ONLY_ISSUES.CONFIGURATION_SCOPE_ID.eq(configurationScopeId)
        .and(LOCAL_ONLY_ISSUES.SERVER_RELATIVE_PATH.eq(filePath.toString())))
      .fetch();
    return issuesInFile.stream()
      .map(LocalOnlyIssuesRepository::recordToLocalOnlyIssue)
      .toList();
  }

  public List<LocalOnlyIssue> loadAll(String configurationScopeId) {
    var allIssues = database.dsl()
      .selectFrom(LOCAL_ONLY_ISSUES)
      .where(LOCAL_ONLY_ISSUES.CONFIGURATION_SCOPE_ID.eq(configurationScopeId))
      .fetch();
    return allIssues.stream()
      .map(LocalOnlyIssuesRepository::recordToLocalOnlyIssue)
      .toList();
  }

  public void storeLocalOnlyIssue(String configurationScopeId, LocalOnlyIssue issue) {
    database.dsl().transaction((Configuration trx) -> {
      var textRangeWithHash = issue.getTextRangeWithHash();
      var startLine = textRangeWithHash == null ? null : textRangeWithHash.getStartLine();
      var startLineOffset = textRangeWithHash == null ? null : textRangeWithHash.getStartLineOffset();
      var endLine = textRangeWithHash == null ? null : textRangeWithHash.getEndLine();
      var endLineOffset = textRangeWithHash == null ? null : textRangeWithHash.getEndLineOffset();
      var textRangeHash = textRangeWithHash == null ? null : textRangeWithHash.getHash();

      var lineWithHash = issue.getLineWithHash();
      var line = lineWithHash == null ? null : lineWithHash.getNumber();
      var lineHash = lineWithHash == null ? null : lineWithHash.getHash();

      var resolution = issue.getResolution();
      var resolutionStatus = resolution == null ? null : resolution.getStatus().name();
      var resolutionDate = resolution == null ? null : LocalDateTime.ofInstant(resolution.getResolutionDate(), ZoneId.systemDefault());
      var comment = resolution == null ? null : resolution.getComment();

      trx.dsl().mergeInto(LOCAL_ONLY_ISSUES)
        .using(trx.dsl().selectOne())
        .on(LOCAL_ONLY_ISSUES.ID.eq(issue.getId()))
        .whenMatchedThenUpdate()
        .set(LOCAL_ONLY_ISSUES.CONFIGURATION_SCOPE_ID, configurationScopeId)
        .set(LOCAL_ONLY_ISSUES.SERVER_RELATIVE_PATH, issue.getServerRelativePath().toString())
        .set(LOCAL_ONLY_ISSUES.RULE_KEY, issue.getRuleKey())
        .set(LOCAL_ONLY_ISSUES.MESSAGE, issue.getMessage())
        .set(LOCAL_ONLY_ISSUES.RESOLUTION_STATUS, resolutionStatus)
        .set(LOCAL_ONLY_ISSUES.RESOLUTION_DATE, resolutionDate)
        .set(LOCAL_ONLY_ISSUES.COMMENT, comment)
        .set(LOCAL_ONLY_ISSUES.START_LINE, startLine)
        .set(LOCAL_ONLY_ISSUES.START_LINE_OFFSET, startLineOffset)
        .set(LOCAL_ONLY_ISSUES.END_LINE, endLine)
        .set(LOCAL_ONLY_ISSUES.END_LINE_OFFSET, endLineOffset)
        .set(LOCAL_ONLY_ISSUES.TEXT_RANGE_HASH, textRangeHash)
        .set(LOCAL_ONLY_ISSUES.LINE, line)
        .set(LOCAL_ONLY_ISSUES.LINE_HASH, lineHash)
        .whenNotMatchedThenInsert(
          LOCAL_ONLY_ISSUES.ID,
          LOCAL_ONLY_ISSUES.CONFIGURATION_SCOPE_ID,
          LOCAL_ONLY_ISSUES.SERVER_RELATIVE_PATH,
          LOCAL_ONLY_ISSUES.RULE_KEY,
          LOCAL_ONLY_ISSUES.MESSAGE,
          LOCAL_ONLY_ISSUES.RESOLUTION_STATUS,
          LOCAL_ONLY_ISSUES.RESOLUTION_DATE,
          LOCAL_ONLY_ISSUES.COMMENT,
          LOCAL_ONLY_ISSUES.START_LINE,
          LOCAL_ONLY_ISSUES.START_LINE_OFFSET,
          LOCAL_ONLY_ISSUES.END_LINE,
          LOCAL_ONLY_ISSUES.END_LINE_OFFSET,
          LOCAL_ONLY_ISSUES.TEXT_RANGE_HASH,
          LOCAL_ONLY_ISSUES.LINE,
          LOCAL_ONLY_ISSUES.LINE_HASH)
        .values(
          issue.getId(),
          configurationScopeId,
          issue.getServerRelativePath().toString(),
          issue.getRuleKey(),
          issue.getMessage(),
          resolutionStatus,
          resolutionDate,
          comment,
          startLine,
          startLineOffset,
          endLine,
          endLineOffset,
          textRangeHash,
          line,
          lineHash)
        .execute();
    });
  }

  public boolean removeIssue(UUID issueId) {
    var deleted = database.dsl()
      .deleteFrom(LOCAL_ONLY_ISSUES)
      .where(LOCAL_ONLY_ISSUES.ID.eq(issueId))
      .execute();
    return deleted > 0;
  }

  public boolean removeAllIssuesForFile(String configurationScopeId, Path filePath) {
    var deleted = database.dsl()
      .deleteFrom(LOCAL_ONLY_ISSUES)
      .where(LOCAL_ONLY_ISSUES.CONFIGURATION_SCOPE_ID.eq(configurationScopeId)
        .and(LOCAL_ONLY_ISSUES.SERVER_RELATIVE_PATH.eq(filePath.toString())))
      .execute();
    return deleted > 0;
  }

  public Optional<LocalOnlyIssue> find(UUID issueId) {
    var issue = database.dsl()
      .selectFrom(LOCAL_ONLY_ISSUES)
      .where(LOCAL_ONLY_ISSUES.ID.eq(issueId))
      .fetchOne();
    return issue == null ? Optional.empty() : Optional.of(recordToLocalOnlyIssue(issue));
  }

  public void purgeIssuesOlderThan(Instant limit) {
    var limitDateTime = LocalDateTime.ofInstant(limit, ZoneId.systemDefault());
    database.dsl()
      .deleteFrom(LOCAL_ONLY_ISSUES)
      .where(LOCAL_ONLY_ISSUES.RESOLUTION_DATE.isNotNull()
        .and(LOCAL_ONLY_ISSUES.RESOLUTION_DATE.le(limitDateTime)))
      .execute();
  }

  private static LocalOnlyIssue recordToLocalOnlyIssue(Record rec) {
    var id = rec.get(LOCAL_ONLY_ISSUES.ID);
    var serverRelativePath = Path.of(rec.get(LOCAL_ONLY_ISSUES.SERVER_RELATIVE_PATH));
    var ruleKey = rec.get(LOCAL_ONLY_ISSUES.RULE_KEY);
    var message = rec.get(LOCAL_ONLY_ISSUES.MESSAGE);

    var textRangeWithHash = getTextRangeWithHash(rec);
    var lineWithHash = getLineWithHash(rec);

    LocalOnlyIssueResolution resolution = null;
    var resolutionStatus = rec.get(LOCAL_ONLY_ISSUES.RESOLUTION_STATUS);
    var resolutionDate = rec.get(LOCAL_ONLY_ISSUES.RESOLUTION_DATE);
    if (resolutionStatus != null && resolutionDate != null) {
      var status = IssueStatus.valueOf(resolutionStatus);
      var instant = resolutionDate.atZone(ZoneId.systemDefault()).toInstant();
      var comment = rec.get(LOCAL_ONLY_ISSUES.COMMENT);
      resolution = new LocalOnlyIssueResolution(status, instant, comment);
    }

    return new LocalOnlyIssue(id, serverRelativePath, textRangeWithHash, lineWithHash, ruleKey, message, resolution);
  }

  private static LineWithHash getLineWithHash(Record rec) {
    var line = rec.get(LOCAL_ONLY_ISSUES.LINE);
    if (line == null) {
      return null;
    }
    var hash = rec.get(LOCAL_ONLY_ISSUES.LINE_HASH);
    return new LineWithHash(line, hash);
  }

  private static TextRangeWithHash getTextRangeWithHash(Record rec) {
    var startLine = rec.get(LOCAL_ONLY_ISSUES.START_LINE);
    if (startLine == null) {
      return null;
    }
    var endLine = rec.get(LOCAL_ONLY_ISSUES.END_LINE);
    var startLineOffset = rec.get(LOCAL_ONLY_ISSUES.START_LINE_OFFSET);
    var endLineOffset = rec.get(LOCAL_ONLY_ISSUES.END_LINE_OFFSET);
    var hash = rec.get(LOCAL_ONLY_ISSUES.TEXT_RANGE_HASH);
    if (hash == null) {
      return null;
    }
    return new TextRangeWithHash(startLine, startLineOffset, endLine, endLineOffset, hash);
  }
}
