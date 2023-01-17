/*
 * SonarLint Core - Implementation
 * Copyright (C) 2016-2023 SonarSource SA
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
package mediumtest.fixtures;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.function.UnaryOperator;
import javax.annotation.Nullable;
import org.sonarsource.sonarlint.core.commons.TextRange;
import org.sonarsource.sonarlint.core.serverapi.UrlUtils;
import org.sonarsource.sonarlint.core.serverapi.proto.sonarqube.ws.Common;
import org.sonarsource.sonarlint.core.serverapi.proto.sonarqube.ws.Hotspots;
import testutils.MockWebServerExtensionWithProtobuf;

public class ServerFixture {
  public static ServerBuilder newSonarQubeServer(String version) {
    return new ServerBuilder(ServerKind.SONARQUBE, version);
  }

  public static ServerBuilder newSonarCloudServer() {
    return new ServerBuilder(ServerKind.SONARQUBE, null);
  }

  private enum ServerKind {
    SONARQUBE, SONARCLOUD
  }

  public static class ServerBuilder {
    private final ServerKind serverKind;
    private final String version;
    private final Map<String, ServerProjectBuilder> projectByProjectKey = new HashMap<>();
    private final Map<String, ServerSourceFileBuilder> sourceFileByComponentKey = new HashMap<>();

    public ServerBuilder(ServerKind serverKind, @Nullable String version) {
      this.serverKind = serverKind;
      this.version = version;
    }

    public ServerBuilder withProject(String projectKey, UnaryOperator<ServerProjectBuilder> projectBuilder) {
      var builder = new ServerProjectBuilder();
      this.projectByProjectKey.put(projectKey, projectBuilder.apply(builder));
      return this;
    }

    public ServerBuilder withSourceFile(String componentKey, UnaryOperator<ServerSourceFileBuilder> sourceFileBuilder) {
      var builder = new ServerSourceFileBuilder();
      this.sourceFileByComponentKey.put(componentKey, sourceFileBuilder.apply(builder));
      return this;
    }

    public Server start() {
      var server = new Server(serverKind, version, projectByProjectKey, sourceFileByComponentKey);
      server.start();
      return server;
    }

    public static class ServerProjectBuilder {

      private final Collection<ServerHotspot> hotspots = new ArrayList<>();

      public ServerProjectBuilder withHotspot(String hotspotKey, String ruleKey, String message, String author, String filePath,
        String status, String resolution, TextRange textRange) {
        this.hotspots.add(new ServerHotspot(hotspotKey, ruleKey, message, author, filePath, status, resolution, textRange));
        return this;
      }

      private static class ServerHotspot {
        private final String hotspotKey;
        private final String ruleKey;
        private final String message;
        private final String author;
        private final String filePath;
        private final String status;
        private final String resolution;
        private final TextRange textRange;

        private ServerHotspot(String hotspotKey, String ruleKey, String message, String author, String filePath, String status,
          String resolution, TextRange textRange) {
          this.hotspotKey = hotspotKey;
          this.ruleKey = ruleKey;
          this.message = message;
          this.author = author;
          this.filePath = filePath;
          this.status = status;
          this.resolution = resolution;
          this.textRange = textRange;
        }
      }
    }

    public static class ServerSourceFileBuilder {
      private String code;

      public ServerSourceFileBuilder withCode(String sourceCode) {
        this.code = sourceCode;
        return this;
      }
    }
  }

  public static class Server {

    private final MockWebServerExtensionWithProtobuf mockWebServer = new MockWebServerExtensionWithProtobuf();
    private final ServerKind serverKind;
    @Nullable
    private final String version;
    private final Map<String, ServerBuilder.ServerProjectBuilder> projectsByProjectKey;
    private final Map<String, ServerBuilder.ServerSourceFileBuilder> sourceFileByComponentKey;

    public Server(ServerKind serverKind, @Nullable String version,
      Map<String, ServerBuilder.ServerProjectBuilder> projectsByProjectKey,
      Map<String, ServerBuilder.ServerSourceFileBuilder> sourceFileByComponentKey) {
      this.serverKind = serverKind;
      this.version = version;
      this.projectsByProjectKey = projectsByProjectKey;
      this.sourceFileByComponentKey = sourceFileByComponentKey;
    }

    public void start() {
      mockWebServer.start();
      registerWebApiResponses();
    }

    private void registerWebApiResponses() {
      registerSystemApiResponses();
      registerHotspotsApiResponses();
      registerSourceApiResponses();
    }

    private void registerSystemApiResponses() {
      mockWebServer.addStringResponse("/api/system/status", "{\"id\": \"20160308094653\",\"version\": \"" + version + "\",\"status\": " +
        "\"UP\"}");
    }

    private void registerHotspotsApiResponses() {
      projectsByProjectKey.forEach((projectKey, project) -> project.hotspots.forEach(hotspot -> {
        var textRange = hotspot.textRange;
        mockWebServer.addProtobufResponse("/api/hotspots/show.protobuf?projectKey=" + projectKey + "&hotspot=" + hotspot.hotspotKey,
          Hotspots.ShowWsResponse.newBuilder()
            .setMessage(hotspot.message)
            .setComponent(Hotspots.Component.newBuilder().setPath(hotspot.filePath).setKey(projectKey + ":" + hotspot.filePath))
            .setTextRange(Common.TextRange.newBuilder().setStartLine(textRange.getStartLine()).setStartOffset(textRange.getStartLineOffset()).setEndLine(textRange.getEndLine())
              .setEndOffset(textRange.getEndLineOffset()).build())
            .setAuthor(hotspot.author)
            .setStatus(hotspot.status)
            .setResolution(hotspot.resolution)
            .setRule(Hotspots.Rule.newBuilder().setKey(hotspot.ruleKey)
              .setName("name")
              .setSecurityCategory("category")
              .setVulnerabilityProbability("HIGH")
              .setRiskDescription("risk")
              .setVulnerabilityDescription("vulnerability")
              .setFixRecommendations("fix")
              .build())
            .build());
      }));
    }

    private void registerSourceApiResponses() {
      sourceFileByComponentKey.forEach((componentKey, sourceFile) -> mockWebServer.addStringResponse("/api/sources/raw?key=" + UrlUtils.urlEncode(componentKey), sourceFile.code));
    }

    public void shutdown() {
      mockWebServer.shutdown();
    }

    public String baseUrl() {
      return mockWebServer.url("");
    }

    public String url(String path) {
      return mockWebServer.url(path);
    }
  }
}
