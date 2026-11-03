/*
 * SonarLint Core - Test Utils
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
package org.sonarsource.sonarlint.core.test.utils.server;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import javax.annotation.Nullable;
import org.sonarsource.sonarlint.core.plugin.source.binaries.BinariesArtifact;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;

public class BinariesServerFixture {

  private BinariesServerFixture() {
    // utility class
  }

  public static BinariesServerBuilder newBinariesServer(@Nullable Consumer<BinariesServer> onStart) {
    return new BinariesServerBuilder(onStart);
  }

  public static class BinariesServerBuilder {
    private final Consumer<BinariesServer> onStart;
    private final Map<BinariesArtifact, ResponseDefinitionBuilder> responsesByArtifact = new EnumMap<>(BinariesArtifact.class);

    private BinariesServerBuilder(@Nullable Consumer<BinariesServer> onStart) {
      this.onStart = onStart;
    }

    public BinariesServerBuilder withArtifact(BinariesArtifact artifact, byte[] body) {
      return withArtifact(artifact, 200, body);
    }

    public BinariesServerBuilder withArtifact(BinariesArtifact artifact, int status) {
      return withArtifact(artifact, status, new byte[0]);
    }

    public BinariesServerBuilder withArtifact(BinariesArtifact artifact, int status, byte[] body) {
      responsesByArtifact.put(artifact, aResponse().withStatus(status).withBody(body));
      return this;
    }

    public BinariesServerBuilder withArtifactFromResource(BinariesArtifact artifact, String resourcePath) {
      return withArtifact(artifact, readResource(resourcePath));
    }

    public BinariesServer start() {
      var server = new BinariesServer(responsesByArtifact);
      server.start();
      if (onStart != null) {
        onStart.accept(server);
      }
      return server;
    }

    private static byte[] readResource(String resourcePath) {
      try (var is = BinariesServerFixture.class.getClassLoader().getResourceAsStream(resourcePath)) {
        if (is == null) {
          throw new IllegalArgumentException("Test resource " + resourcePath + " must be on the classpath");
        }
        return is.readAllBytes();
      } catch (IOException e) {
        throw new UncheckedIOException(e);
      }
    }
  }

  public static class BinariesServer {
    private static final AtomicBoolean ACTIVE = new AtomicBoolean();

    private final WireMockServer mockServer = new WireMockServer(options().dynamicPort());
    private final Map<BinariesArtifact, ResponseDefinitionBuilder> responsesByArtifact;
    @Nullable
    private String previousUrlProperty;

    private BinariesServer(Map<BinariesArtifact, ResponseDefinitionBuilder> responsesByArtifact) {
      this.responsesByArtifact = Map.copyOf(responsesByArtifact);
    }

    public void start() {
      if (!ACTIVE.compareAndSet(false, true)) {
        throw new IllegalStateException("A fake binaries server is already running. " + BinariesArtifact.PROPERTY_URL_PATTERN
          + " is a JVM-wide property, so only one can be active at a time");
      }
      try {
        mockServer.start();
        responsesByArtifact.forEach(this::registerArtifact);
        previousUrlProperty = System.getProperty(BinariesArtifact.PROPERTY_URL_PATTERN);
        System.setProperty(BinariesArtifact.PROPERTY_URL_PATTERN, mockServer.baseUrl());
      } catch (RuntimeException e) {
        ACTIVE.set(false);
        throw e;
      }
    }

    public void shutdown() {
      try {
        mockServer.stop();
      } finally {
        restoreUrlProperty();
        ACTIVE.set(false);
      }
    }

    public String baseUrl() {
      return mockServer.baseUrl();
    }

    public int requestCount(BinariesArtifact artifact) {
      return mockServer.countRequestsMatching(getRequestedFor(urlEqualTo(pathOf(artifact))).build()).getCount();
    }

    private void registerArtifact(BinariesArtifact artifact, ResponseDefinitionBuilder response) {
      mockServer.stubFor(get(urlEqualTo(pathOf(artifact))).willReturn(response));
    }

    private void restoreUrlProperty() {
      if (previousUrlProperty == null) {
        System.clearProperty(BinariesArtifact.PROPERTY_URL_PATTERN);
      } else {
        System.setProperty(BinariesArtifact.PROPERTY_URL_PATTERN, previousUrlProperty);
      }
    }

    private static String pathOf(BinariesArtifact artifact) {
      return URI.create(String.format(artifact.urlPattern(), artifact.version())).getPath();
    }
  }
}
