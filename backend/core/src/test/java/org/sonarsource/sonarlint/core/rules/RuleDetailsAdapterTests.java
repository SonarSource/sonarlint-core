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
package org.sonarsource.sonarlint.core.rules;

import org.junit.jupiter.api.Test;
import org.sonarsource.sonarlint.core.commons.CleanCodeAttribute;
import org.sonarsource.sonarlint.core.commons.CleanCodeAttributeCategory;
import org.sonarsource.sonarlint.core.commons.ImpactSeverity;
import org.sonarsource.sonarlint.core.commons.IssueSeverity;
import org.sonarsource.sonarlint.core.commons.api.SonarLanguage;
import org.sonarsource.sonarlint.core.commons.RuleType;
import org.sonarsource.sonarlint.core.commons.SoftwareQuality;

import static org.assertj.core.api.Assertions.assertThat;
import static org.sonarsource.sonarlint.core.rules.RuleDetailsAdapter.adapt;

class RuleDetailsAdapterTests {

  @Test
  void it_should_adapt_all_cca_enum_values() {
    for (var cca : CleanCodeAttribute.values()) {
      var adapted = adapt(cca);
      assertThat(adapted.name()).isEqualTo(cca.name());
    }
  }

  @Test
  void it_should_adapt_all_ccac_enum_values() {
    for (var ccac : CleanCodeAttributeCategory.values()) {
      var adapted = adapt(ccac);
      assertThat(adapted.name()).isEqualTo(ccac.name());
    }
  }

  @Test
  void it_should_adapt_all_severity_enum_values() {
    for (var s : IssueSeverity.values()) {
      var adapted = adapt(s);
      assertThat(adapted.name()).isEqualTo(s.name());
    }
  }

  @Test
  void it_should_adapt_all_ruletype_enum_values() {
    for (var t : RuleType.values()) {
      var adapted = adapt(t);
      assertThat(adapted.name()).isEqualTo(t.name());
    }
  }

  @Test
  void it_should_adapt_all_language_enum_values() {
    for (var l : SonarLanguage.values()) {
      var adapted = adapt(l);
      assertThat(adapted.name()).isEqualTo(l.name());
    }
  }

  @Test
  void it_should_adapt_all_impact_severity_enum_values() {
    for (var is : ImpactSeverity.values()) {
      var adapted = adapt(is);
      assertThat(adapted.name()).isEqualTo(is.name());
    }
  }

  @Test
  void it_should_adapt_all_software_quality_enum_values() {
    for (var sq : SoftwareQuality.values()) {
      var adapted = adapt(sq);
      assertThat(adapted.name()).isEqualTo(sq.name());
    }
  }

}
