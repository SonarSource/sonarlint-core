/*
 * SonarLint Core - Implementation
 * Copyright (C) 2016-2022 SonarSource SA
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

import javax.annotation.Nullable;
import org.sonarsource.sonarlint.core.serverapi.EndpointParams;
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

    public ServerBuilder(ServerKind serverKind, @Nullable String version) {
      this.serverKind = serverKind;
      this.version = version;
    }

    public Server start() {
      return new Server(serverKind, version);
    }
  }

  public static class Server {

    private final MockWebServerExtensionWithProtobuf mockWebServer;
    private final EndpointParams endpointParams;

    public Server(ServerKind serverKind, @Nullable String version) {
      mockWebServer = new MockWebServerExtensionWithProtobuf();
      mockWebServer.start();
      endpointParams = new EndpointParams(mockWebServer.url(""), ServerKind.SONARCLOUD.equals(serverKind), null);
      registerWebApiResponses(version);
    }

    private void registerWebApiResponses(String version) {
      registerStatusWebApi(version);
    }

    private void registerStatusWebApi(@Nullable String version) {
      mockWebServer.addStringResponse("/api/system/status", "{\"id\": \"20160308094653\",\"version\": \"" + version + "\",\"status\": \"UP\"}");
    }

    public void shutdown() {
      mockWebServer.shutdown();
    }

    public EndpointParams getEndpointParams() {
      return endpointParams;
    }

    public String baseUrl() {
      return endpointParams.getBaseUrl();
    }

    public String url(String path) {
      return mockWebServer.url(path);
    }
  }
}
