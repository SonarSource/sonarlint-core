/*
 * SonarLint Core - Medium Tests
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
package mediumtest.issues;

import java.nio.file.Paths;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.sonarsource.sonarlint.core.commons.RuleType;
import org.sonarsource.sonarlint.core.commons.api.TextRangeWithHash;
import org.sonarsource.sonarlint.core.test.utils.junit5.SonarLintTest;
import org.sonarsource.sonarlint.core.test.utils.junit5.SonarLintTestHarness;
import uk.org.webcompere.systemstubs.environment.EnvironmentVariables;
import uk.org.webcompere.systemstubs.jupiter.SystemStub;
import uk.org.webcompere.systemstubs.jupiter.SystemStubsExtension;

import static mediumtest.fixtures.LocalOnlyIssueFixtures.aLocalOnlyIssueResolved;
import static org.assertj.core.api.Assertions.assertThat;
import static org.sonarsource.sonarlint.core.commons.dogfood.DogfoodEnvironmentDetectionService.SONARSOURCE_DOGFOODING_ENV_VAR_KEY;
import static org.sonarsource.sonarlint.core.test.utils.storage.ServerIssueFixtures.aServerIssue;

@ExtendWith(SystemStubsExtension.class)
class LocalOnlyResolvedIssuesStorageMediumTests {

  @SystemStub
  EnvironmentVariables environmentVariables;

  @BeforeEach
  void prepare() {
    environmentVariables.remove(SONARSOURCE_DOGFOODING_ENV_VAR_KEY);
  }

  @SonarLintTest
  void it_should_purge_local_only_stored_issues_resolved_more_than_one_week_ago_at_startup(SonarLintTestHarness harness) {
    var serverIssue = aServerIssue("myIssueKey").withTextRange(new TextRangeWithHash(1, 2, 3, 4, "hash")).withIntroductionDate(Instant.EPOCH.plusSeconds(1)).withType(RuleType.BUG);
    var server = harness.newFakeSonarQubeServer().start();
    var backend = harness.newBackend()
      .withSonarQubeConnection("connectionId", server.baseUrl(), storage -> storage
        .withProject("projectKey", project -> project.withMainBranch("main", branch -> branch.withIssue(serverIssue)))
        .withServerVersion("9.8"))
      .withBoundConfigScope("configScopeId", "connectionId", "projectKey",
        storage -> storage.withLocalOnlyIssue(aLocalOnlyIssueResolved(Instant.now().minus(1, ChronoUnit.MINUTES).minus(7, ChronoUnit.DAYS))))
      .start();

    var storedIssues = backend.getLocalOnlyIssuesRepository().loadAll("configScopeId");

    assertThat(storedIssues).isEmpty();
  }

  @SonarLintTest
  void it_should_migrate_the_local_only_issues_from_xodus_to_the_new_h2_database(SonarLintTestHarness harness) {
    environmentVariables.set(SONARSOURCE_DOGFOODING_ENV_VAR_KEY, "1");
    var serverIssue = aServerIssue("myIssueKey").withTextRange(new TextRangeWithHash(1, 2, 3, 4, "hash")).withIntroductionDate(Instant.EPOCH.plusSeconds(1)).withType(RuleType.BUG);
    var backend = harness.newBackend()
      .withSonarQubeConnection("connectionId", harness.newFakeSonarQubeServer().start(), storage -> storage
        .withProject("projectKey", project -> project.withMainBranch("main", branch -> branch.withIssue(serverIssue)))
        .withServerVersion("9.8"))
      .withBoundConfigScope("configScopeId", "connectionId", "projectKey",
        storage -> storage
          .usingXodus()
          .withLocalOnlyIssue(aLocalOnlyIssueResolved()))
      .start();

    var issues = backend.getLocalOnlyIssuesRepository().loadForFile("configScopeId", Paths.get("file/path"));

    assertThat(issues).hasSize(1);
  }
}
