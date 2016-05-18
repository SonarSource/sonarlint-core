/*
 * SonarLint Core - ITs
 * Copyright (C) 2009-2016 SonarSource SA
 * mailto:contact AT sonarsource DOT com
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
import com.sonar.orchestrator.container.Server;
import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import org.junit.ClassRule;
import org.junit.rules.TemporaryFolder;
import org.sonarqube.ws.client.HttpConnector;
import org.sonarqube.ws.client.WsClient;
import org.sonarqube.ws.client.WsClientFactories;
import org.sonarsource.sonarlint.core.client.api.common.analysis.ClientInputFile;
import org.sonarsource.sonarlint.core.client.api.common.analysis.Issue;
import org.sonarsource.sonarlint.core.client.api.common.analysis.IssueListener;
import org.sonarsource.sonarlint.core.client.api.connected.ConnectedAnalysisConfiguration;

import static com.sonar.orchestrator.container.Server.ADMIN_LOGIN;
import static com.sonar.orchestrator.container.Server.ADMIN_PASSWORD;

public class AbstractConnectedTest {
  protected static final String SONARLINT_USER = "sonarlint";
  protected static final String SONARLINT_PWD = "sonarlintpwd";

  @ClassRule
  public static TemporaryFolder t = new TemporaryFolder();

  public static WsClient newAdminWsClient(Orchestrator orchestrator) {
    Server server = orchestrator.getServer();
    return WsClientFactories.getDefault().newClient(HttpConnector.newBuilder()
      .url(server.getUrl())
      .credentials(ADMIN_LOGIN, ADMIN_PASSWORD)
      .build());
  }

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

  protected ConnectedAnalysisConfiguration createAnalysisConfiguration(String projectKey, String projectDir, String filePath, String... properties) throws IOException {
    return new ConnectedAnalysisConfiguration(projectKey,
      new File("projects/" + projectDir).toPath().toAbsolutePath(),
      t.newFolder().toPath(),
      Arrays.asList(inputFile(projectDir, filePath)),
      toMap(properties));
  }

  protected ClientInputFile inputFile(final String projectDir, final String relativePath) {
    return new ClientInputFile() {

      @Override
      public boolean isTest() {
        return false;
      }

      @Override
      public Path getPath() {
        return Paths.get("projects/" + projectDir + "/" + relativePath).toAbsolutePath();
      }

      @Override
      public <G> G getClientObject() {
        return null;
      }

      @Override
      public Charset getCharset() {
        return null;
      }
    };
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
