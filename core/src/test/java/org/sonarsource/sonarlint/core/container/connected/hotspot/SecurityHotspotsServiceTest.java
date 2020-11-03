/*
 * SonarLint Core - Implementation
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
package org.sonarsource.sonarlint.core.container.connected.hotspot;

import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.Parser;
import java.io.InputStream;
import java.util.Optional;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.sonarqube.ws.Common;
import org.sonarqube.ws.Hotspots;
import org.sonarsource.sonarlint.core.WsClientTestUtils;
import org.sonarsource.sonarlint.core.client.api.common.TextRange;
import org.sonarsource.sonarlint.core.client.api.connected.GetSecurityHotspotRequestParams;
import org.sonarsource.sonarlint.core.client.api.connected.RemoteHotspot;
import org.sonarsource.sonarlint.core.container.connected.SonarLintWsClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class SecurityHotspotsServiceTest {

  @Mock
  private SonarLintWsClient wsClient;
  @Mock
  private Parser<Hotspots.ShowWsResponse> parser;
  @Captor
  private ArgumentCaptor<InputStream> captor;
  private SecurityHotspotsService service;

  @Before
  public void setUp() {
    service = new SecurityHotspotsService(wsClient, parser);
  }

  @Test
  public void it_should_call_the_expected_api_endpoint_when_fetching_hotspot_details() {
    service.fetch(new GetSecurityHotspotRequestParams("h", "p"));

    verify(wsClient).get("/api/hotspots/show.protobuf?projectKey=p&hotspot=h");
  }

  @Test
  public void it_should_urlencode_the_hotspot_and_project_keys_when_fetching_hotspot_details() {
    service.fetch(new GetSecurityHotspotRequestParams("hot/spot", "pro/ject"));

    verify(wsClient).get("/api/hotspots/show.protobuf?projectKey=pro%2Fject&hotspot=hot%2Fspot");
  }

  @Test
  public void it_should_parse_the_response() throws InvalidProtocolBufferException {
    WsClientTestUtils.addStreamResponse(this.wsClient, "response");

    service.fetch(new GetSecurityHotspotRequestParams("h", "p"));

    verify(parser).parseFrom(captor.capture());
    assertThat(captor.getValue()).hasContent("response");
  }

  @Test
  public void it_should_adapt_and_return_the_hotspot() throws InvalidProtocolBufferException {
    WsClientTestUtils.addStreamResponse(this.wsClient, "response");
    when(parser.parseFrom((InputStream) any())).thenReturn(Hotspots.ShowWsResponse.newBuilder()
      .setMessage("message")
      .setComponent(Hotspots.Component.newBuilder().setPath("path"))
      .setTextRange(Common.TextRange.newBuilder().setStartLine(1).setStartOffset(2).setEndLine(3).setEndOffset(4).build())
      .setAuthor("author")
      .setStatus("REVIEWED")
      .setResolution("SAFE")
      .setRule(Hotspots.Rule.newBuilder().
        setKey("key")
        .setName("name")
        .setSecurityCategory("category")
        .setVulnerabilityProbability("HIGH")
        .setRiskDescription("risk")
        .setVulnerabilityDescription("vulnerability")
        .setFixRecommendations("fix")
        .build())
      .build());

    Optional<RemoteHotspot> remoteHotspot = service.fetch(new GetSecurityHotspotRequestParams("h", "p"));

    assertThat(remoteHotspot).isNotEmpty();
    RemoteHotspot hotspot = remoteHotspot.get();
    assertThat(hotspot.message).isEqualTo("message");
    assertThat(hotspot.filePath).isEqualTo("path");
    assertThat(hotspot.textRange).isEqualToComparingFieldByField(new TextRange(1, 2, 3, 4));
    assertThat(hotspot.author).isEqualTo("author");
    assertThat(hotspot.status).isEqualTo(RemoteHotspot.Status.REVIEWED);
    assertThat(hotspot.resolution).isEqualTo(RemoteHotspot.Resolution.SAFE);
    assertThat(hotspot.rule.key).isEqualTo("key");
    assertThat(hotspot.rule.name).isEqualTo("name");
    assertThat(hotspot.rule.securityCategory).isEqualTo("category");
    assertThat(hotspot.rule.vulnerabilityProbability).isEqualTo(RemoteHotspot.Rule.Probability.HIGH);
    assertThat(hotspot.rule.riskDescription).isEqualTo("risk");
    assertThat(hotspot.rule.vulnerabilityDescription).isEqualTo("vulnerability");
    assertThat(hotspot.rule.fixRecommendations).isEqualTo("fix");
  }

  @Test
  public void it_should_return_empty_optional_when_ws_client_throws_an_exception() {
    doThrow(new RuntimeException("Error")).when(wsClient).get(any());

    Optional<RemoteHotspot> remoteHotspot = service.fetch(new GetSecurityHotspotRequestParams("h", "p"));

    assertThat(remoteHotspot).isEmpty();
  }

  @Test
  public void it_should_return_empty_optional_when_parser_throws_an_exception() throws InvalidProtocolBufferException {
    WsClientTestUtils.addStreamResponse(this.wsClient, "response");
    doThrow(new InvalidProtocolBufferException("Error")).when(parser).parseFrom((InputStream) any());

    Optional<RemoteHotspot> remoteHotspot = service.fetch(new GetSecurityHotspotRequestParams("h", "p"));

    assertThat(remoteHotspot).isEmpty();
  }

  @Test
  public void it_should_return_no_resolution_status_when_not_available() throws InvalidProtocolBufferException {
    WsClientTestUtils.addStreamResponse(this.wsClient, "response");
    when(parser.parseFrom((InputStream) any())).thenReturn(Hotspots.ShowWsResponse.newBuilder()
      .setComponent(Hotspots.Component.newBuilder().setPath("path"))
      .setTextRange(Common.TextRange.newBuilder().setStartLine(1).setStartOffset(2).setEndLine(3).setEndOffset(4).build())
      .setStatus("TO_REVIEW")
      .setRule(Hotspots.Rule.newBuilder().
        setKey("key")
        .setName("name")
        .setSecurityCategory("category")
        .setVulnerabilityProbability("HIGH")
        .setRiskDescription("risk")
        .setVulnerabilityDescription("vulnerability")
        .setFixRecommendations("fix")
        .build())
      .build());

    Optional<RemoteHotspot> remoteHotspot = service.fetch(new GetSecurityHotspotRequestParams("h", "p"));

    assertThat(remoteHotspot).isNotEmpty();
    RemoteHotspot hotspot = remoteHotspot.get();
    assertThat(hotspot.resolution).isNull();
  }
}
