package org.sonarsource.sonarlint.core.rules;

import org.junit.jupiter.api.Test;
import org.sonarsource.sonarlint.core.commons.CleanCodeAttribute;
import org.sonarsource.sonarlint.core.commons.CleanCodeAttributeCategory;
import org.sonarsource.sonarlint.core.commons.ImpactSeverity;
import org.sonarsource.sonarlint.core.commons.IssueSeverity;
import org.sonarsource.sonarlint.core.commons.Language;
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
    for (var l : Language.values()) {
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
