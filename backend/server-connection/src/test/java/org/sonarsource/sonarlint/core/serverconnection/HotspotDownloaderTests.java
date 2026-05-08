/*
 * SonarLint Core - Server Connection
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
package org.sonarsource.sonarlint.core.serverconnection;

import java.nio.file.Path;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.sonarsource.sonarlint.core.commons.HotspotReviewStatus;
import org.sonarsource.sonarlint.core.commons.VulnerabilityProbability;
import org.sonarsource.sonarlint.core.commons.api.SonarLanguage;
import org.sonarsource.sonarlint.core.commons.api.TextRangeWithHash;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogTester;
import org.sonarsource.sonarlint.core.commons.progress.SonarLintCancelMonitor;
import org.sonarsource.sonarlint.core.serverapi.ServerApi;
import org.sonarsource.sonarlint.core.serverapi.hotspot.ServerHotspot;
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
    underTest = new HotspotDownloader(Set.of(SonarLanguage.JAVA));
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

    var result = underTest.downloadFromPull(serverApi.hotspot(), DUMMY_KEY, "myBranch", Optional.empty(), new SonarLintCancelMonitor());
    assertThat(result.getChangedHotspots()).hasSize(2);
    assertThat(result.getClosedHotspotKeys()).isEmpty();

    var serverHotspot1 = result.getChangedHotspots().get(0);
    assertHotspotFields(serverHotspot1, "someHotspotKey", HotspotReviewStatus.TO_REVIEW, 1, 2, 3, 4, "clearly not a hash");

    var serverHotspot2 = result.getChangedHotspots().get(1);
    assertHotspotFields(serverHotspot2, "otherHotspotKey", HotspotReviewStatus.SAFE, 5, 6, 7, 8, "not a hash either");
  }

  private static void assertHotspotFields(ServerHotspot hotspot, String expectedKey, HotspotReviewStatus expectedStatus,
    int expectedStartLine, int expectedStartLineOffset, int expectedEndLine, int expectedEndLineOffset, String expectedHash) {
    assertThat(hotspot.getKey()).isEqualTo(expectedKey);
    assertThat(hotspot.getFilePath()).isEqualTo(Path.of("foo/bar/Hello.java"));
    assertThat(hotspot.getVulnerabilityProbability()).isEqualTo(VulnerabilityProbability.LOW);
    assertThat(hotspot.getStatus()).isEqualTo(expectedStatus);
    assertThat(hotspot.getMessage()).isEqualTo("This is security sensitive");
    assertThat(hotspot.getCreationDate()).isAfter(Instant.EPOCH);
    assertThat(hotspot.getTextRange().getStartLine()).isEqualTo(expectedStartLine);
    assertThat(hotspot.getTextRange().getStartLineOffset()).isEqualTo(expectedStartLineOffset);
    assertThat(hotspot.getTextRange().getEndLine()).isEqualTo(expectedEndLine);
    assertThat(hotspot.getTextRange().getEndLineOffset()).isEqualTo(expectedEndLineOffset);
    assertThat(((TextRangeWithHash) hotspot.getTextRange()).getHash()).isEqualTo(expectedHash);
    assertThat(hotspot.getRuleKey()).isEqualTo("java:S123");
  }
}
