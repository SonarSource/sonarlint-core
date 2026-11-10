/*
 * SonarLint Core - Medium Tests
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
package mediumtest.storage;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.sonarsource.sonarlint.core.commons.IssueStatus;
import org.sonarsource.sonarlint.core.commons.KnownFinding;
import org.sonarsource.sonarlint.core.commons.LineWithHash;
import org.sonarsource.sonarlint.core.commons.LocalOnlyIssue;
import org.sonarsource.sonarlint.core.commons.LocalOnlyIssueResolution;
import org.sonarsource.sonarlint.core.commons.RuleType;
import org.sonarsource.sonarlint.core.commons.api.TextRangeWithHash;
import org.sonarsource.sonarlint.core.commons.storage.SonarLintDatabase;
import org.sonarsource.sonarlint.core.serverconnection.issues.KnownFindingsRepository;
import org.sonarsource.sonarlint.core.serverconnection.issues.ServerDependencyRisk;
import org.sonarsource.sonarlint.core.test.utils.junit5.SonarLintTest;
import org.sonarsource.sonarlint.core.test.utils.junit5.SonarLintTestHarness;

import static mediumtest.storage.StorageSnapshot.CONFIG_SCOPE_ID;
import static mediumtest.storage.StorageSnapshot.CONNECTION_ID;
import static mediumtest.storage.StorageSnapshot.KNOWN_FINDINGS_FILE;
import static mediumtest.storage.StorageSnapshot.PROJECT_KEY;
import static org.sonarsource.sonarlint.core.test.utils.storage.ServerDependencyRiskFixtures.aServerDependencyRisk;
import static org.sonarsource.sonarlint.core.test.utils.storage.ServerIssueFixtures.aServerIssue;
import static org.sonarsource.sonarlint.core.test.utils.storage.ServerSecurityHotspotFixture.aServerHotspot;

/**
 * Not a test: writes a storage snapshot for {@link StorageUpgradeMediumTests}. It runs on a release tag, see storage-snapshots/README.md:
 * <pre>
 * mvn test -pl medium-tests -am -Dtest=StorageSnapshotGeneratorMediumTests -Dsurefire.failIfNoSpecifiedTests=false \
 *   -Dsonarlint.storageSnapshot.release=12.3.0.87938 \
 *   -Dsonarlint.storageSnapshot.outputDir=$PWD/medium-tests/src/test/resources/storage-snapshots/12.3.0.87938
 * </pre>
 */
@EnabledIfSystemProperty(named = StorageSnapshotGeneratorMediumTests.OUTPUT_DIR_PROPERTY, matches = ".+")
class StorageSnapshotGeneratorMediumTests {

  static final String OUTPUT_DIR_PROPERTY = "sonarlint.storageSnapshot.outputDir";
  static final String RELEASE_PROPERTY = "sonarlint.storageSnapshot.release";

  // Fixed values keep the content of successive snapshots comparable
  private static final Instant CREATION_DATE = Instant.parse("2026-01-01T00:00:00Z");
  // Resolved local-only issues are purged one week after their resolution at every startup
  private static final Instant FAR_FUTURE = Instant.parse("2100-01-01T00:00:00Z");
  private static final TextRangeWithHash TEXT_RANGE = new TextRangeWithHash(1, 2, 3, 4, "rangeHash");
  private static final String FILE_PATH = "src/main/java/Foo.java";

  @SonarLintTest
  void generate_storage_snapshot(SonarLintTestHarness harness) throws Exception {
    var release = System.getProperty(RELEASE_PROPERTY);
    if (release == null || release.isBlank()) {
      throw new IllegalArgumentException("Missing system property " + RELEASE_PROPERTY);
    }
    var outputDir = Path.of(System.getProperty(OUTPUT_DIR_PROPERTY));
    var storageRoot = Files.createTempDirectory("storageSnapshot").resolve("storage");

    var backend = harness.newBackend()
      .withStorageRoot(storageRoot)
      .withSonarQubeConnection(CONNECTION_ID, storage -> storage
        .withServerVersion("2025.1")
        .withAiCodeFixSettings(aiCodeFix -> aiCodeFix.withSupportedRules(Set.of("java:S1481")).organizationEligible(true).enabledForProjects(PROJECT_KEY))
        .withProject(PROJECT_KEY, project -> project.withMainBranch("main", branch -> branch
          .withIssue(aServerIssue("issueKey1").withFilePath(FILE_PATH).withTextRange(TEXT_RANGE).withIntroductionDate(CREATION_DATE).withType(RuleType.BUG))
          .withIssue(aServerIssue("issueKey2").withFilePath(FILE_PATH).withTextRange(TEXT_RANGE).withIntroductionDate(CREATION_DATE).withType(RuleType.CODE_SMELL))
          .withHotspot(aServerHotspot("hotspotKey").withFilePath(FILE_PATH).withTextRange(TEXT_RANGE).withIntroductionDate(CREATION_DATE))
          .withDependencyRisk(aServerDependencyRisk()
            .withKey(UUID.fromString("550e8400-e29b-41d4-a716-446655440000"))
            .withPackageName("com.example.vulnerable")
            .withPackageVersion("2.1.0")
            .withVulnerabilityId("CVE-1234")
            .withCvssScore("7.5")
            .withType(ServerDependencyRisk.Type.VULNERABILITY)
            .withSeverity(ServerDependencyRisk.Severity.HIGH)))))
      .withBoundConfigScope(CONFIG_SCOPE_ID, CONNECTION_ID, PROJECT_KEY, storage -> storage
        .withLocalOnlyIssue(new LocalOnlyIssue(UUID.fromString("7c9e6679-7425-40de-944b-e07fc1f90ae7"), Path.of(FILE_PATH), TEXT_RANGE, new LineWithHash(1, "lineHash"),
          "java:S1481", "Remove this unused variable", new LocalOnlyIssueResolution(IssueStatus.WONT_FIX, FAR_FUTURE, "comment"))))
      .start();

    // Known findings are only stored by the backend itself, after an analysis
    var knownFindingsRepository = new KnownFindingsRepository(backend.getSonarLintDatabase());
    knownFindingsRepository.storeKnownIssues(CONFIG_SCOPE_ID, Path.of(KNOWN_FINDINGS_FILE),
      List.of(new KnownFinding(UUID.fromString("a3bb189e-8bf9-3888-9912-ace4e6543002"), "issueKey1", TEXT_RANGE, new LineWithHash(1, "lineHash"), "java:S1481",
        "Remove this unused variable", CREATION_DATE)));
    knownFindingsRepository.storeKnownSecurityHotspots(CONFIG_SCOPE_ID, Path.of(KNOWN_FINDINGS_FILE),
      List.of(new KnownFinding(UUID.fromString("b4cc290f-9cf0-4999-aa23-bdf5f7654113"), "hotspotKey", TEXT_RANGE, new LineWithHash(1, "lineHash"), "java:S2068",
        "Review this hard-coded password", CREATION_DATE)));

    var dsl = backend.getSonarLintDatabase().dsl();
    var metadata = StorageSnapshot.Metadata.of(release, dsl);
    var schema = StorageSnapshot.schemaScript(dsl);
    // Close the database cleanly before copying it
    harness.shutdown(backend);

    FileUtils.deleteDirectory(outputDir.toFile());
    var outputH2 = Files.createDirectories(outputDir.resolve("h2"));
    var dbFileName = SonarLintDatabase.SQ_IDE_DB_FILENAME + ".mv.db";
    Files.copy(storageRoot.resolve("h2").resolve(dbFileName), outputH2.resolve(dbFileName), StandardCopyOption.REPLACE_EXISTING);
    metadata.write(outputDir);
    Files.writeString(outputDir.resolve(StorageSnapshot.SCHEMA_FILE), schema, StandardCharsets.UTF_8);
  }
}
