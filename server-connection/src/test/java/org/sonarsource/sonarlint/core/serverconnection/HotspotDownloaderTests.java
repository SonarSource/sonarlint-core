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
import org.sonarsource.sonarlint.core.serverapi.ServerApi;
import org.sonarsource.sonarlint.core.serverapi.proto.sonarqube.ws.Hotspots;
import testutils.MockWebServerExtensionWithProtobuf;

import static org.assertj.core.api.Assertions.assertThat;

class HotspotDownloaderTests {

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
    var hotspot = Hotspots.HotspotLite.newBuilder()
      .setKey("someHotspotKey")
      .setFilePath("foo/bar/Hello.java")
      .setVulnerabilityProbability(VulnerabilityProbability.LOW.toString())
      .setStatus(HotspotReviewStatus.TO_REVIEW.toString())
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
      .setReviewedSafe(false)
      .setClosed(false)
      .build();

    mockServer.addProtobufResponseDelimited("/api/hotspots/pull?projectKey=" + DUMMY_KEY + "&branchName=myBranch&languages=java", timestamp, hotspot);

    var result = underTest.downloadFromPull(serverApi, DUMMY_KEY, "myBranch", Optional.empty());
    assertThat(result.getChangedHotspots()).hasSize(1);
    assertThat(result.getClosedHotspotKeys()).isEmpty();

    var serverHotspot = result.getChangedHotspots().get(0);
    assertThat(serverHotspot.getKey()).isEqualTo("someHotspotKey");
    assertThat(serverHotspot.getFilePath()).isEqualTo("foo/bar/Hello.java");
    assertThat(serverHotspot.getVulnerabilityProbability()).isEqualTo(VulnerabilityProbability.LOW);
    assertThat(serverHotspot.getStatus()).isEqualTo(HotspotReviewStatus.TO_REVIEW);
    assertThat(serverHotspot.getMessage()).isEqualTo("This is security sensitive");
    assertThat(serverHotspot.getCreationDate()).isAfter(Instant.EPOCH);
    assertThat(serverHotspot.getTextRange().getStartLine()).isEqualTo(1);
    assertThat(serverHotspot.getTextRange().getStartLineOffset()).isEqualTo(2);
    assertThat(serverHotspot.getTextRange().getEndLine()).isEqualTo(3);
    assertThat(serverHotspot.getTextRange().getEndLineOffset()).isEqualTo(4);
    assertThat(((TextRangeWithHash) serverHotspot.getTextRange()).getHash()).isEqualTo("clearly not a hash");
    assertThat(serverHotspot.getRuleKey()).isEqualTo("java:S123");
  }
}
