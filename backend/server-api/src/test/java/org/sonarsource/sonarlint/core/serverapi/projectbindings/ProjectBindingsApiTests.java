/*
 * SonarLint Core - Server API
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
package org.sonarsource.sonarlint.core.serverapi.projectbindings;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import mockwebserver3.MockResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogTester;
import org.sonarsource.sonarlint.core.commons.progress.SonarLintCancelMonitor;
import org.sonarsource.sonarlint.core.serverapi.MockWebServerExtensionWithProtobuf;

import static org.assertj.core.api.Assertions.assertThat;

class ProjectBindingsApiTests {
  @RegisterExtension
  private static final SonarLintLogTester logTester = new SonarLintLogTester();

  @RegisterExtension
  static MockWebServerExtensionWithProtobuf mockServer = new MockWebServerExtensionWithProtobuf();

  private ProjectBindingsApi underTest;

  @BeforeEach
  void setUp() {
    underTest = new ProjectBindingsApi(mockServer.serverApiHelper());
  }

  @Nested
  class SonarQubeCloud {
    @Test
    void should_return_project_id_by_url() {
      var url = "https://github.com/foo/bar";
      var encodedUrl = URLEncoder.encode(url, StandardCharsets.UTF_8);
      mockServer.addStringResponse("/dop-translation/project-bindings?url=" + encodedUrl,
        "{\"bindings\":[{\"projectId\":\"proj:123\"}]}");

      var result = underTest.getSQCProjectBindings(url, new SonarLintCancelMonitor());

      assertThat(result).isEqualTo(new SQCProjectBindingsResponse("proj:123"));
    }

    @Test
    void should_return_empty_when_no_bindings() {
      var url = "https://github.com/foo/bar";
      var encodedUrl = URLEncoder.encode(url, StandardCharsets.UTF_8);
      mockServer.addStringResponse("/dop-translation/project-bindings?url=" + encodedUrl,
        "{\"bindings\":[]}");

      var result = underTest.getSQCProjectBindings(url, new SonarLintCancelMonitor());

      assertThat(result).isNull();
    }

    @Test
    void should_return_empty_when_invalid_json() {
      var url = "https://github.com/foo/bar";
      var encodedUrl = URLEncoder.encode(url, StandardCharsets.UTF_8);
      mockServer.addStringResponse("/dop-translation/project-bindings?url=" + encodedUrl,
        "this is not json");

      var result = underTest.getSQCProjectBindings(url, new SonarLintCancelMonitor());

      assertThat(result).isNull();
    }

    @Test
    void should_return_empty_when_request_fails() {
      var url = "https://github.com/foo/bar";
      var encodedUrl = URLEncoder.encode(url, StandardCharsets.UTF_8);
      mockServer.addResponse("/dop-translation/project-bindings?url=" + encodedUrl,
        new MockResponse.Builder().code(500).body("Internal error").build());

      var result = underTest.getSQCProjectBindings(url, new SonarLintCancelMonitor());

      assertThat(result).isNull();
    }
  }

  @Nested
  class SonarQubeServer {
    @Test
    void should_return_project_key_by_url() {
      var url = "https://github.com/foo/bar";
      var encodedUrl = URLEncoder.encode(url, StandardCharsets.UTF_8);
      mockServer.addStringResponse("/api/v2/dop-translation/project-bindings?repositoryUrl=" + encodedUrl,
        "{\"projectBindings\":[{\"projectId\":\"proj:123\",\"projectKey\":\"my-project-key\"}]}");

      var result = underTest.getSQSProjectBindings(url, new SonarLintCancelMonitor());

      assertThat(result).isEqualTo(new SQSProjectBindingsResponse("proj:123", "my-project-key"));
    }

    @Test
    void should_return_empty_when_no_bindings() {
      var url = "https://github.com/foo/bar";
      var encodedUrl = URLEncoder.encode(url, StandardCharsets.UTF_8);
      mockServer.addStringResponse("/api/v2/dop-translation/project-bindings?repositoryUrl=" + encodedUrl,
        "{\"projectBindings\":[]}");

      var result = underTest.getSQSProjectBindings(url, new SonarLintCancelMonitor());

      assertThat(result).isNull();
    }

    @Test
    void should_return_empty_when_invalid_json() {
      var url = "https://github.com/foo/bar";
      var encodedUrl = URLEncoder.encode(url, StandardCharsets.UTF_8);
      mockServer.addStringResponse("/api/v2/dop-translation/project-bindings?repositoryUrl=" + encodedUrl,
        "this is not json");

      var result = underTest.getSQSProjectBindings(url, new SonarLintCancelMonitor());

      assertThat(result).isNull();
    }

    @Test
    void should_return_empty_when_request_fails() {
      var url = "https://github.com/foo/bar";
      var encodedUrl = URLEncoder.encode(url, StandardCharsets.UTF_8);
      mockServer.addResponse("/api/v2/dop-translation/project-bindings?repositoryUrl=" + encodedUrl,
        new MockResponse.Builder().code(500).body("Internal error").build());

      var result = underTest.getSQSProjectBindings(url, new SonarLintCancelMonitor());

      assertThat(result).isNull();
    }
  }
}
