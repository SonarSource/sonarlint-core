/*
 * SonarLint Core - Server Connection
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
package org.sonarsource.sonarlint.core.serverconnection;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.sonarsource.sonarlint.core.commons.HotspotReviewStatus;
import org.sonarsource.sonarlint.core.commons.Language;
import org.sonarsource.sonarlint.core.commons.TextRangeWithHash;
import org.sonarsource.sonarlint.core.commons.VulnerabilityProbability;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogTester;
import org.sonarsource.sonarlint.core.serverapi.ServerApi;
import org.sonarsource.sonarlint.core.serverapi.proto.sonarqube.ws.Hotspots;
import testutils.MockWebServerExtensionWithProtobuf;

import static org.assertj.core.api.Assertions.assertThat;

class HotspotDownloaderTests {
  @RegisterExtension
  private static final SonarLintLogTester logTester = new SonarLintLogTester();

  private static final String DUMMY_KEY = "dummyKey";

  @RegisterExtension
  static MockWebServerExtensionWithProtobuf mockServer = new MockWebServerExtensionWithProtobuf();
  private ServerApi serverApi;

  private HotspotDownloader underTest;

  @BeforeEach
  void prepare() {
    underTest = new HotspotDownloader(Set.of(Language.JAVA));
    serverApi = new ServerApi(mockServer.serverApiHelper());
  }

  @Test
  void test_download_one_hotspot_pull_ws() {
    var timestamp = Hotspots.HotspotPullQueryTimestamp.newBuilder().setQueryTimestamp(123L).build();
    var hotspot1 = Hotspots.HotspotLite.newBuilder()
      .setKey("someHotspotKey")
      .setFilePath("foo/bar/Hello.java")
      .setVulnerabilityProbability(VulnerabilityProbability.LOW.toString())
      .setStatus("TO_REVIEW")
      .setMessage("This is security sensitive")
      .setCreationDate(123456789L)
      .setTextRange(Hotspots.TextRange.newBuilder()
        .setStartLine(1)
        .setStartLineOffset(2)
        .setEndLine(3)
        .setEndLineOffset(4)
        .setHash("clearly not a hash")
        .build())
      .setRuleKey("java:S123")
      .setClosed(false)
      .build();
    var hotspot2 = Hotspots.HotspotLite.newBuilder()
      .setKey("otherHotspotKey")
      .setFilePath("foo/bar/Hello.java")
      .setVulnerabilityProbability(VulnerabilityProbability.LOW.toString())
      .setStatus("REVIEWED")
      .setResolution("SAFE")
      .setMessage("This is security sensitive")
      .setCreationDate(123456789L)
      .setTextRange(Hotspots.TextRange.newBuilder()
        .setStartLine(5)
        .setStartLineOffset(6)
        .setEndLine(7)
        .setEndLineOffset(8)
        .setHash("not a hash either")
        .build())
      .setRuleKey("java:S123")
      .setClosed(false)
      .build();

    mockServer.addProtobufResponseDelimited("/api/hotspots/pull?projectKey=" + DUMMY_KEY + "&branchName=myBranch&languages=java", timestamp, hotspot1, hotspot2);

    var result = underTest.downloadFromPull(serverApi.hotspot(), DUMMY_KEY, "myBranch", Optional.empty());
    assertThat(result.getChangedHotspots()).hasSize(2);
    assertThat(result.getClosedHotspotKeys()).isEmpty();

    var serverHotspot1 = result.getChangedHotspots().get(0);
    assertThat(serverHotspot1.getKey()).isEqualTo("someHotspotKey");
    assertThat(serverHotspot1.getFilePath()).isEqualTo("foo/bar/Hello.java");
    assertThat(serverHotspot1.getVulnerabilityProbability()).isEqualTo(VulnerabilityProbability.LOW);
    assertThat(serverHotspot1.getStatus()).isEqualTo(HotspotReviewStatus.TO_REVIEW);
    assertThat(serverHotspot1.getMessage()).isEqualTo("This is security sensitive");
    assertThat(serverHotspot1.getCreationDate()).isAfter(Instant.EPOCH);
    assertThat(serverHotspot1.getTextRange().getStartLine()).isEqualTo(1);
    assertThat(serverHotspot1.getTextRange().getStartLineOffset()).isEqualTo(2);
    assertThat(serverHotspot1.getTextRange().getEndLine()).isEqualTo(3);
    assertThat(serverHotspot1.getTextRange().getEndLineOffset()).isEqualTo(4);
    assertThat(((TextRangeWithHash) serverHotspot1.getTextRange()).getHash()).isEqualTo("clearly not a hash");
    assertThat(serverHotspot1.getRuleKey()).isEqualTo("java:S123");

    var serverHotspot2 = result.getChangedHotspots().get(1);
    assertThat(serverHotspot2.getKey()).isEqualTo("otherHotspotKey");
    assertThat(serverHotspot2.getFilePath()).isEqualTo("foo/bar/Hello.java");
    assertThat(serverHotspot2.getVulnerabilityProbability()).isEqualTo(VulnerabilityProbability.LOW);
    assertThat(serverHotspot2.getStatus()).isEqualTo(HotspotReviewStatus.SAFE);
    assertThat(serverHotspot2.getMessage()).isEqualTo("This is security sensitive");
    assertThat(serverHotspot2.getCreationDate()).isAfter(Instant.EPOCH);
    assertThat(serverHotspot2.getTextRange().getStartLine()).isEqualTo(5);
    assertThat(serverHotspot2.getTextRange().getStartLineOffset()).isEqualTo(6);
    assertThat(serverHotspot2.getTextRange().getEndLine()).isEqualTo(7);
    assertThat(serverHotspot2.getTextRange().getEndLineOffset()).isEqualTo(8);
    assertThat(((TextRangeWithHash) serverHotspot2.getTextRange()).getHash()).isEqualTo("not a hash either");
    assertThat(serverHotspot2.getRuleKey()).isEqualTo("java:S123");
  }
}
