/*
 * SonarLint Core - Commons
 * Copyright (C) 2016-2025 SonarSource SA
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
package org.sonarsource.sonarlint.core.commons;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HotspotReviewStatusTest {
  @Test
  void should_be_resolved_when_fixed_or_safe() {
    assertThat(HotspotReviewStatus.SAFE.isResolved()).isTrue();
    assertThat(HotspotReviewStatus.FIXED.isResolved()).isTrue();
    assertThat(HotspotReviewStatus.ACKNOWLEDGED.isResolved()).isFalse();
    assertThat(HotspotReviewStatus.TO_REVIEW.isResolved()).isFalse();
  }
  @Test
  void should_be_reviewed_when_fixed_or_safe_or_acknowledged() {
    assertThat(HotspotReviewStatus.SAFE.isReviewed()).isTrue();
    assertThat(HotspotReviewStatus.FIXED.isReviewed()).isTrue();
    assertThat(HotspotReviewStatus.ACKNOWLEDGED.isReviewed()).isTrue();
    assertThat(HotspotReviewStatus.TO_REVIEW.isReviewed()).isFalse();
  }
}