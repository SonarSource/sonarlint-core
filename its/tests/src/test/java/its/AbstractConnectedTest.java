/*
 * SonarLint Core - ITs - Tests
 * Copyright (C) 2016-2020 SonarSource SA
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
package its;

import com.google.common.base.Preconditions;
import com.google.common.collect.Maps;
import com.sonar.orchestrator.Orchestrator;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import org.junit.ClassRule;
import org.junit.rules.TemporaryFolder;
import org.sonarqube.ws.client.HttpConnector;
import org.sonarqube.ws.client.WsClient;
import org.sonarqube.ws.client.WsClientFactories;
import org.sonarsource.sonarlint.core.client.api.common.analysis.Issue;
import org.sonarsource.sonarlint.core.client.api.common.analysis.IssueListener;
import org.sonarsource.sonarlint.core.client.api.connected.ConnectedAnalysisConfiguration;

public abstract class AbstractConnectedTest {
  protected static final String SONARLINT_USER = "sonarlint";
  protected static final String SONARLINT_PWD = "sonarlintpwd";

  @ClassRule
  public static TemporaryFolder t = new TemporaryFolder();

  protected static class SaveIssueListener implements IssueListener {
    List<Issue> issues = new LinkedList<>();

    @Override
    public void handle(Issue issue) {
      issues.add(issue);
    }

    public List<Issue> getIssues() {
      return issues;
    }

    public void clear() {
      issues.clear();
    }
  }

  protected static WsClient newAdminWsClient(Orchestrator orchestrator) {
    com.sonar.orchestrator.container.Server server = orchestrator.getServer();
    return WsClientFactories.getDefault().newClient(HttpConnector.newBuilder()
      .url(server.getUrl())
      .credentials(com.sonar.orchestrator.container.Server.ADMIN_LOGIN, com.sonar.orchestrator.container.Server.ADMIN_PASSWORD)
      .build());
  }

  protected ConnectedAnalysisConfiguration createAnalysisConfiguration(String projectKey, String projectDir, String filePath, String... properties) throws IOException {
    final Path baseDir = Paths.get("projects/" + projectDir).toAbsolutePath();
    final Path path = baseDir.resolve(filePath);
    return ConnectedAnalysisConfiguration.builder()
      .setProjectKey(projectKey)
      .setBaseDir(new File("projects/" + projectDir).toPath().toAbsolutePath())
      .addInputFile(new TestClientInputFile(baseDir, path, false, StandardCharsets.UTF_8))
      .putAllExtraProperties(toMap(properties))
      .build();
  }

  protected ConnectedAnalysisConfiguration createAnalysisConfiguration(String projectKey, String absoluteFilePath) throws IOException {
    final Path path = Paths.get(absoluteFilePath).toAbsolutePath();
    return ConnectedAnalysisConfiguration.builder()
      .setProjectKey(projectKey)
      .setBaseDir(path.getParent())
      .addInputFile(new TestClientInputFile(path.getParent(), path, false, StandardCharsets.UTF_8))
      .build();
  }

  static Map<String, String> toMap(String[] keyValues) {
    Preconditions.checkArgument(keyValues.length % 2 == 0, "Must be an even number of key/values");
    Map<String, String> map = Maps.newHashMap();
    int index = 0;
    while (index < keyValues.length) {
      String key = keyValues[index++];
      String value = keyValues[index++];
      map.put(key, value);
    }
    return map;
  }
}
