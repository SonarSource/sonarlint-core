/*
 * SonarLint Core - Server API
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
package org.sonarsource.sonarlint.core.serverapi.hotspot;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.sonarsource.sonarlint.core.commons.TextRange;
import org.sonarsource.sonarlint.core.commons.Version;
import org.sonarsource.sonarlint.core.commons.VulnerabilityProbability;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogTester;
import org.sonarsource.sonarlint.core.commons.progress.ProgressMonitor;
import org.sonarsource.sonarlint.core.commons.testutils.MockWebServerExtension;
import org.sonarsource.sonarlint.core.serverapi.EndpointParams;
import org.sonarsource.sonarlint.core.serverapi.MockWebServerExtensionWithProtobuf;
import org.sonarsource.sonarlint.core.serverapi.ServerApi;
import org.sonarsource.sonarlint.core.serverapi.ServerApiHelper;
import org.sonarsource.sonarlint.core.serverapi.UrlUtils;
import org.sonarsource.sonarlint.core.serverapi.proto.sonarqube.ws.Common;
import org.sonarsource.sonarlint.core.serverapi.proto.sonarqube.ws.Hotspots;
import org.sonarsource.sonarlint.core.serverapi.proto.sonarqube.ws.Issues;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.junit.jupiter.api.Assertions.assertThrows;

class HotspotApiTests {

  @RegisterExtension
  static MockWebServerExtensionWithProtobuf mockServer = new MockWebServerExtensionWithProtobuf();

  @RegisterExtension
  public SonarLintLogTester logTester = new SonarLintLogTester();

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
  void it_should_adapt_and_return_the_hotspot_details() {
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
    assertThat(hotspot.textRange).usingRecursiveComparison().isEqualTo(new TextRange(2, 7, 4, 9));
    assertThat(hotspot.author).isEqualTo("author");
    assertThat(hotspot.status).isEqualTo(ServerHotspotDetails.Status.REVIEWED);
    assertThat(hotspot.resolution).isEqualTo(ServerHotspotDetails.Resolution.SAFE);
    assertThat(hotspot.rule.key).isEqualTo("key");
    assertThat(hotspot.rule.name).isEqualTo("name");
    assertThat(hotspot.rule.securityCategory).isEqualTo("category");
    assertThat(hotspot.rule.vulnerabilityProbability).isEqualTo(VulnerabilityProbability.HIGH);
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

  @Test
  void it_should_fetch_project_hotspots() {
    mockServer.addProtobufResponse("/api/hotspots/search.protobuf?projectKey=p&branch=branch&ps=500&p=1", Hotspots.SearchWsResponse.newBuilder()
      .setPaging(Common.Paging.newBuilder().setTotal(1).build())
      .addHotspots(Hotspots.SearchWsResponse.Hotspot.newBuilder()
        .setComponent("component:path1")
        .setTextRange(Common.TextRange.newBuilder().setStartLine(1).setStartOffset(2).setEndLine(3).setEndOffset(4).build())
        .setStatus("TO_REVIEW")
        .setKey("hotspotKey1")
        .setCreationDate("2020-09-21T12:46:39+0000")
        .setRuleKey("ruleKey1")
        .setMessage("message1")
        .setVulnerabilityProbability("HIGH")
        .build())
      .addHotspots(Hotspots.SearchWsResponse.Hotspot.newBuilder()
        .setComponent("component:path2")
        .setTextRange(Common.TextRange.newBuilder().setStartLine(5).setStartOffset(6).setEndLine(7).setEndOffset(8).build())
        .setStatus("REVIEWED")
        .setResolution("SAFE")
        .setKey("hotspotKey2")
        .setCreationDate("2020-09-22T12:46:39+0000")
        .setRuleKey("ruleKey2")
        .setMessage("message2")
        .setVulnerabilityProbability("LOW")
        .build())
      .addHotspots(Hotspots.SearchWsResponse.Hotspot.newBuilder()
        .setComponent("component:path3")
        .setTextRange(Common.TextRange.newBuilder().setStartLine(9).setStartOffset(10).setEndLine(11).setEndOffset(12).build())
        .setStatus("REVIEWED")
        .setResolution("ACKNOWLEDGED")
        .setKey("hotspotKey3")
        .setCreationDate("2020-09-23T12:46:39+0000")
        .setRuleKey("ruleKey3")
        .setMessage("message3")
        .setVulnerabilityProbability("LOW")
        .build())
      .addHotspots(Hotspots.SearchWsResponse.Hotspot.newBuilder()
        .setComponent("component:path4")
        .setTextRange(Common.TextRange.newBuilder().setStartLine(13).setStartOffset(14).setEndLine(15).setEndOffset(16).build())
        .setStatus("REVIEWED")
        .setResolution("FIXED")
        .setKey("hotspotKey4")
        .setCreationDate("2020-09-24T12:46:39+0000")
        .setRuleKey("ruleKey4")
        .setMessage("message4")
        .setVulnerabilityProbability("MEDIUM")
        .build())
      .addHotspots(Hotspots.SearchWsResponse.Hotspot.newBuilder()
        .setComponent("component:path5")
        .setTextRange(Common.TextRange.newBuilder().setStartLine(17).setStartOffset(18).setEndLine(19).setEndOffset(20).build())
        .setStatus("REVIEWED")
        .setKey("hotspotKey5")
        .setCreationDate("2020-09-25T12:46:39+0000")
        .setRuleKey("ruleKey5")
        .setMessage("message5")
        .setVulnerabilityProbability("LOW")
        .build())
      .addComponents(Hotspots.Component.newBuilder().setKey("component:path1").setPath("path1").build())
      .addComponents(Hotspots.Component.newBuilder().setKey("component:path2").setPath("path2").build())
      .addComponents(Hotspots.Component.newBuilder().setKey("component:path3").setPath("path3").build())
      .addComponents(Hotspots.Component.newBuilder().setKey("component:path4").setPath("path4").build())
      .addComponents(Hotspots.Component.newBuilder().setKey("component:path5").setPath("path5").build())
      .build());

    var hotspots = underTest.getAll("p", "branch", new ProgressMonitor(null));

    assertThat(hotspots)
      .extracting("key", "ruleKey", "message", "filePath", "textRange.startLine", "textRange.startLineOffset", "textRange.endLine", "textRange.endLineOffset", "creationDate",
        "resolved")
      .containsExactly(
        tuple("hotspotKey1", "ruleKey1", "message1", "path1", 1, 2, 3, 4, LocalDateTime.of(2020, 9, 21, 12, 46, 39).toInstant(ZoneOffset.UTC), false),
        tuple("hotspotKey2", "ruleKey2", "message2", "path2", 5, 6, 7, 8, LocalDateTime.of(2020, 9, 22, 12, 46, 39).toInstant(ZoneOffset.UTC), true),
        tuple("hotspotKey3", "ruleKey3", "message3", "path3", 9, 10, 11, 12, LocalDateTime.of(2020, 9, 23, 12, 46, 39).toInstant(ZoneOffset.UTC), false),
        tuple("hotspotKey4", "ruleKey4", "message4", "path4", 13, 14, 15, 16, LocalDateTime.of(2020, 9, 24, 12, 46, 39).toInstant(ZoneOffset.UTC), true),
        tuple("hotspotKey5", "ruleKey5", "message5", "path5", 17, 18, 19, 20, LocalDateTime.of(2020, 9, 25, 12, 46, 39).toInstant(ZoneOffset.UTC), false));
  }

  @Test
  void it_should_fetch_file_hotspots() {
    mockServer.addProtobufResponse("/api/hotspots/search.protobuf?projectKey=p&files=path%2Fto%2Ffile.ext&branch=branch&ps=500&p=1", Hotspots.SearchWsResponse.newBuilder()
      .setPaging(Common.Paging.newBuilder().setTotal(1).build())
      .addHotspots(Hotspots.SearchWsResponse.Hotspot.newBuilder()
        .setComponent("component:path/to/file.ext")
        .setTextRange(Common.TextRange.newBuilder().setStartLine(1).setStartOffset(2).setEndLine(3).setEndOffset(4).build())
        .setStatus("TO_REVIEW")
        .setKey("hotspotKey1")
        .setCreationDate("2020-09-21T12:46:39+0000")
        .setRuleKey("ruleKey1")
        .setMessage("message1")
        .setVulnerabilityProbability("HIGH")
        .build())
      .addComponents(Hotspots.Component.newBuilder().setKey("component:path/to/file.ext").setPath("path/to/file.ext").build())
      .build());

    var hotspots = underTest.getFromFile("p", "path/to/file.ext", "branch");

    assertThat(hotspots)
      .extracting("key", "ruleKey", "message", "filePath", "textRange.startLine", "textRange.startLineOffset", "textRange.endLine", "textRange.endLineOffset", "creationDate",
        "resolved")
      .containsExactly(
        tuple("hotspotKey1", "ruleKey1", "message1", "path/to/file.ext", 1, 2, 3, 4, ZonedDateTime.of(2020, 9, 21, 12, 46, 39, 0, ZoneId.of("UTC")).toInstant(), false));
  }

  @Test
  void it_should_log_when_hotspot_component_is_missing() {
    mockServer.addProtobufResponse("/api/hotspots/search.protobuf?projectKey=p&branch=branch&ps=500&p=1", Hotspots.SearchWsResponse.newBuilder()
      .setPaging(Common.Paging.newBuilder().setTotal(1).build())
      .addHotspots(Hotspots.SearchWsResponse.Hotspot.newBuilder()
        .setComponent("component:path")
        .build())
      .build());

    var hotspots = underTest.getAll("p", "branch", new ProgressMonitor(null));

    assertThat(logTester.logs())
      .contains("Error while fetching security hotspots, the component 'component:path' is missing");
  }

  @Test
  void sonar_cloud_should_not_permit_tracking_hotspots() {
    var hotspotApi = new HotspotApi(new ServerApiHelper(new EndpointParams("https://sonarcloud.io", true, "org"), MockWebServerExtension.httpClient()));

    var permitsTracking = hotspotApi.permitsTracking(null);

    assertThat(permitsTracking).isFalse();
  }

  @Test
  void sonar_qube_prior_to_9_7_should_not_permit_tracking_hotspots() {
    var hotspotApi = new HotspotApi(new ServerApiHelper(new EndpointParams("http://my.sonar.qube", false, null), MockWebServerExtension.httpClient()));

    var permitsTracking = hotspotApi.permitsTracking(() -> Version.create("9.6.0"));

    assertThat(permitsTracking).isFalse();
  }

  @Test
  void sonar_qube_9_7_plus_should_permit_tracking_hotspots() {
    var hotspotApi = new HotspotApi(new ServerApiHelper(new EndpointParams("http://my.sonar.qube", false, null), MockWebServerExtension.httpClient()));

    var permitsTracking = hotspotApi.permitsTracking(() -> Version.create("9.7.0"));

    assertThat(permitsTracking).isTrue();
  }
}
