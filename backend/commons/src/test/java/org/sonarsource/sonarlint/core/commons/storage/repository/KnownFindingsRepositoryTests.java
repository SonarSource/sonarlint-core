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
import java.util.ArrayList;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;
import org.sonarsource.sonarlint.core.commons.KnownFinding;
import org.sonarsource.sonarlint.core.commons.LineWithHash;
import org.sonarsource.sonarlint.core.commons.api.TextRangeWithHash;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogTester;
import org.sonarsource.sonarlint.core.commons.storage.SonarLintDatabase;
import org.sonarsource.sonarlint.core.commons.storage.SonarLintDatabaseInitParams;
import org.sonarsource.sonarlint.core.commons.storage.SonarLintDatabaseMode;

import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;

class KnownFindingsRepositoryTests {

  @RegisterExtension
  static SonarLintLogTester logTester = new SonarLintLogTester();

  @Test
  void testKnownFindingsRepository(@TempDir Path temp) {
    var storageRoot = temp.resolve("storage");

    var db = new SonarLintDatabase(new SonarLintDatabaseInitParams(storageRoot, SonarLintDatabaseMode.MEM));
    var repo = new KnownFindingsRepository(db);

    var filePath = Path.of("/file/path");
    var configScopeId = "configScopeId";
    var issues = new ArrayList<KnownFinding>();
    var issueUuid1 = UUID.randomUUID();
    var issueIntroDate1 = Instant.now();
    var issue1 = new KnownFinding(issueUuid1, "test-message", new TextRangeWithHash(1, 2, 3, 4, "hash1"),
      new LineWithHash(1, "hash"), "test-issue-rule-1", "Test issue message 1", issueIntroDate1);
    issues.add(issue1);
    var issueUuid2 = UUID.randomUUID();
    var issueIntroDate2 = Instant.now();
    var issue2 = new KnownFinding(issueUuid2, "test-message", new TextRangeWithHash(5, 6, 7, 8, "hash2"),
      new LineWithHash(1, "hash"), "test-issue-rule-2", "Test issue message 2", issueIntroDate2);
    issues.add(issue2);
    var hotspots = new ArrayList<KnownFinding>();
    var hotspotUuid1 = UUID.randomUUID();
    var hotspotIntroDate1 = Instant.now();
    var hotspot1 = new KnownFinding(hotspotUuid1, "test-message", new TextRangeWithHash(1, 2, 3, 4, "hash1"),
      new LineWithHash(1, "hash"), "test-hotspot-rule-1", "Test hotspot message 1", hotspotIntroDate1);
    hotspots.add(hotspot1);
    var hotspotUuid2 = UUID.randomUUID();
    var hotspotIntroDate2 = Instant.now();
    var hotspot2 = new KnownFinding(hotspotUuid2, "test-message", new TextRangeWithHash(5, 6, 7, 8, "hash2"),
      new LineWithHash(1, "hash"), "test-hotspot-rule-2", "Test hotspot message 2", hotspotIntroDate2);
    hotspots.add(hotspot2);

    repo.storeKnownIssues(configScopeId, filePath, issues);
    repo.storeKnownSecurityHotspots(configScopeId, filePath, hotspots);

    var knownIssues = repo.loadIssuesForFile(configScopeId, filePath);
    var knownHotspots = repo.loadSecurityHotspotsForFile(configScopeId, filePath);

    assertThat(knownIssues).hasSize(2);
    assertThat(knownHotspots).hasSize(2);
    var knownIssue = knownIssues.get(0);
    assertThat(knownIssue.getRuleKey()).isEqualTo(issue1.getRuleKey());
    assertThat(knownIssue.getServerKey()).isEqualTo(issue1.getServerKey());
    assertThat(knownIssue.getMessage()).isEqualTo(issue1.getMessage());
    assertThat(knownIssue.getTextRangeWithHash()).isEqualTo(issue1.getTextRangeWithHash());
    assertThat(knownIssue.getLineWithHash().getNumber()).isEqualTo(issue1.getLineWithHash().getNumber());
    assertThat(knownIssue.getLineWithHash().getHash()).isEqualTo(issue1.getLineWithHash().getHash());
    var knownHotspot = knownHotspots.get(0);
    assertThat(knownHotspot.getRuleKey()).isEqualTo(hotspot1.getRuleKey());
    assertThat(knownHotspot.getServerKey()).isEqualTo(hotspot1.getServerKey());
    assertThat(knownHotspot.getMessage()).isEqualTo(hotspot1.getMessage());
    assertThat(knownHotspot.getTextRangeWithHash()).isEqualTo(hotspot1.getTextRangeWithHash());
    assertThat(knownHotspot.getLineWithHash().getNumber()).isEqualTo(hotspot1.getLineWithHash().getNumber());
    assertThat(knownHotspot.getLineWithHash().getHash()).isEqualTo(hotspot1.getLineWithHash().getHash());
  }

}
