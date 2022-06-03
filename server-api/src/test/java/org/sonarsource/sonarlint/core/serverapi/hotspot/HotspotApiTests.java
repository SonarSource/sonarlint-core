/*
 * SonarLint Server API
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
package org.sonarsource.sonarlint.core.serverapi.hotspot;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.sonarsource.sonarlint.core.commons.testutils.MockWebServerExtension;
import org.sonarsource.sonarlint.core.serverapi.MockWebServerExtensionWithProtobuf;
import org.sonarsource.sonarlint.core.serverapi.ServerApi;
import org.sonarsource.sonarlint.core.serverapi.UrlUtils;
import org.sonarsource.sonarlint.core.serverapi.proto.sonarqube.ws.Common;
import org.sonarsource.sonarlint.core.serverapi.proto.sonarqube.ws.Hotspots;
import org.sonarsource.sonarlint.core.serverapi.proto.sonarqube.ws.Issues;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class HotspotApiTests {

  @RegisterExtension
  static MockWebServerExtensionWithProtobuf mockServer = new MockWebServerExtensionWithProtobuf();

  private HotspotApi underTest;

  @BeforeEach
  void setUp() {
    underTest = new ServerApi(mockServer.endpointParams(), MockWebServerExtension.httpClient()).hotspot();
  }

  @Test
  void it_should_call_the_expected_api_endpoint_when_fetching_hotspot_details() {
    underTest.fetch(new GetSecurityHotspotRequestParams("h", "p"));

    var recordedRequest = mockServer.takeRequest();
    assertThat(recordedRequest.getPath()).isEqualTo("/api/hotspots/show.protobuf?projectKey=p&hotspot=h");
  }

  @Test
  void it_should_urlencode_the_hotspot_and_project_keys_when_fetching_hotspot_details() {
    underTest.fetch(new GetSecurityHotspotRequestParams("hot/spot", "pro/ject"));

    var recordedRequest = mockServer.takeRequest();
    assertThat(recordedRequest.getPath()).isEqualTo("/api/hotspots/show.protobuf?projectKey=pro%2Fject&hotspot=hot%2Fspot");
  }

  @Test
  void it_should_adapt_and_return_the_hotspot() {
    mockServer.addProtobufResponse("/api/hotspots/show.protobuf?projectKey=p&hotspot=h", Hotspots.ShowWsResponse.newBuilder()
      .setMessage("message")
      .setComponent(Hotspots.Component.newBuilder().setPath("path").setKey("myproject:path"))
      .setTextRange(Common.TextRange.newBuilder().setStartLine(2).setStartOffset(7).setEndLine(4).setEndOffset(9).build())
      .setAuthor("author")
      .setStatus("REVIEWED")
      .setResolution("SAFE")
      .setRule(Hotspots.Rule.newBuilder().setKey("key")
        .setName("name")
        .setSecurityCategory("category")
        .setVulnerabilityProbability("HIGH")
        .setRiskDescription("risk")
        .setVulnerabilityDescription("vulnerability")
        .setFixRecommendations("fix")
        .build())
      .build());
    mockServer.addStringResponse("/api/sources/raw?key=" + UrlUtils.urlEncode("myproject:path"), "Even\nBefore My\n\tCode\n  Snippet And\n After");

    var remoteHotspot = underTest.fetch(new GetSecurityHotspotRequestParams("h", "p"));

    assertThat(remoteHotspot).isNotEmpty();
    var hotspot = remoteHotspot.get();
    assertThat(hotspot.message).isEqualTo("message");
    assertThat(hotspot.filePath).isEqualTo("path");
    assertThat(hotspot.textRange).usingRecursiveComparison().isEqualTo(new ServerHotspot.TextRange(2, 7, 4, 9));
    assertThat(hotspot.author).isEqualTo("author");
    assertThat(hotspot.status).isEqualTo(ServerHotspot.Status.REVIEWED);
    assertThat(hotspot.resolution).isEqualTo(ServerHotspot.Resolution.SAFE);
    assertThat(hotspot.rule.key).isEqualTo("key");
    assertThat(hotspot.rule.name).isEqualTo("name");
    assertThat(hotspot.rule.securityCategory).isEqualTo("category");
    assertThat(hotspot.rule.vulnerabilityProbability).isEqualTo(ServerHotspot.Rule.Probability.HIGH);
    assertThat(hotspot.rule.riskDescription).isEqualTo("risk");
    assertThat(hotspot.rule.vulnerabilityDescription).isEqualTo("vulnerability");
    assertThat(hotspot.rule.fixRecommendations).isEqualTo("fix");
    assertThat(hotspot.codeSnippet).isEqualTo("My\n\tCode\n  Snippet");
  }

  @Test
  void it_should_extract_single_line_snippet() {
    mockServer.addProtobufResponse("/api/hotspots/show.protobuf?projectKey=p&hotspot=h", Hotspots.ShowWsResponse.newBuilder()
      .setMessage("message")
      .setComponent(Hotspots.Component.newBuilder().setPath("path").setKey("myproject:path"))
      .setTextRange(Common.TextRange.newBuilder().setStartLine(2).setStartOffset(7).setEndLine(2).setEndOffset(9).build())
      .setAuthor("author")
      .setStatus("REVIEWED")
      .setResolution("SAFE")
      .setRule(Hotspots.Rule.newBuilder().setKey("key")
        .setName("name")
        .setSecurityCategory("category")
        .setVulnerabilityProbability("HIGH")
        .setRiskDescription("risk")
        .setVulnerabilityDescription("vulnerability")
        .setFixRecommendations("fix")
        .build())
      .build());
    mockServer.addStringResponse("/api/sources/raw?key=" + UrlUtils.urlEncode("myproject:path"), "Even\nBefore My\n\tCode\n  Snippet And\n After");

    var remoteHotspot = underTest.fetch(new GetSecurityHotspotRequestParams("h", "p"));

    assertThat(remoteHotspot).isNotEmpty();
    var hotspot = remoteHotspot.get();
    assertThat(hotspot.codeSnippet).isEqualTo("My");
  }

  @Test
  void it_should_return_empty_optional_when_ws_client_throws_an_exception() {
    var remoteHotspot = underTest.fetch(new GetSecurityHotspotRequestParams("h", "p"));
    assertThat(remoteHotspot).isEmpty();
  }

  @Test
  void it_should_throw_when_parser_throws_an_exception() {
    mockServer.addProtobufResponse("/api/hotspots/show.protobuf?projectKey=p&hotspot=h", Issues.SearchWsResponse.newBuilder().build());

    var params = new GetSecurityHotspotRequestParams("h", "p");
    assertThrows(IllegalArgumentException.class, () -> underTest.fetch(params));
  }

  @Test
  void it_should_return_no_resolution_status_when_not_available() {
    mockServer.addProtobufResponse("/api/hotspots/show.protobuf?projectKey=p&hotspot=h", Hotspots.ShowWsResponse.newBuilder()
      .setComponent(Hotspots.Component.newBuilder().setPath("path"))
      .setTextRange(Common.TextRange.newBuilder().setStartLine(1).setStartOffset(2).setEndLine(3).setEndOffset(4).build())
      .setStatus("TO_REVIEW")
      .setRule(Hotspots.Rule.newBuilder().setKey("key")
        .setName("name")
        .setSecurityCategory("category")
        .setVulnerabilityProbability("HIGH")
        .setRiskDescription("risk")
        .setVulnerabilityDescription("vulnerability")
        .setFixRecommendations("fix")
        .build())
      .build());

    var remoteHotspot = underTest.fetch(new GetSecurityHotspotRequestParams("h", "p"));

    assertThat(remoteHotspot).isNotEmpty();
    var hotspot = remoteHotspot.get();
    assertThat(hotspot.resolution).isNull();
  }
}
