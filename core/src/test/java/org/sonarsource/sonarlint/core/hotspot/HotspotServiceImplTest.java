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
package org.sonarsource.sonarlint.core.hotspot;

import org.junit.jupiter.api.Test;
import org.sonarsource.sonarlint.core.serverapi.EndpointParams;

import static org.assertj.core.api.Assertions.assertThat;

class HotspotServiceImplTest {

  @Test
  void testBuildSonarQubeHotspotUrl() {
    assertThat(HotspotServiceImpl.buildHotspotUrl("myProject", "myBranch", "hotspotKey", new EndpointParams("http://foo.com", false, null)))
      .isEqualTo("http://foo.com/security_hotspots?id=myProject&branch=myBranch&hotspots=hotspotKey");
  }

  @Test
  void testBuildSonarCloudHotspotUrl() {
    assertThat(HotspotServiceImpl.buildHotspotUrl("myProject", "myBranch", "hotspotKey", new EndpointParams("https://sonarcloud.io", true, "myOrg")))
      .isEqualTo("https://sonarcloud.io/project/security_hotspots?id=myProject&branch=myBranch&hotspots=hotspotKey");
  }
}