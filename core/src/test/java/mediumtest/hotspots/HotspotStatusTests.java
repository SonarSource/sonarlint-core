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
package mediumtest.hotspots;

import org.junit.jupiter.api.Test;
import org.sonarsource.sonarlint.core.clientapi.backend.hotspot.HotspotStatus;
import org.sonarsource.sonarlint.core.commons.HotspotReviewStatus;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class HotspotStatusTests {

  @Test
  void valueOfTitleTest() {
    assertThat(HotspotStatus.valueOfTitle("To Review")).isEqualTo(HotspotStatus.TO_REVIEW);
    assertThat(HotspotStatus.valueOfTitle("Safe")).isEqualTo(HotspotStatus.SAFE);
    assertThat(HotspotStatus.valueOfTitle("Fixed")).isEqualTo(HotspotStatus.FIXED);
    assertThat(HotspotStatus.valueOfTitle("Acknowledged")).isEqualTo(HotspotStatus.ACKNOWLEDGED);
    var thrown = assertThrows(IllegalArgumentException.class, ()-> HotspotStatus.valueOfTitle("Unknown"));
    assertThat(thrown).hasMessage("There is no such title of the hotspot status: Unknown");
  }

  @Test
  void valueOfHotspotReviewStatusTest() {
    for (HotspotReviewStatus value : HotspotReviewStatus.values()) {
      assertThat(HotspotStatus.valueOfHotspotReviewStatus(value)).isEqualTo(HotspotStatus.valueOf(value.name()));
    }
  }

}
