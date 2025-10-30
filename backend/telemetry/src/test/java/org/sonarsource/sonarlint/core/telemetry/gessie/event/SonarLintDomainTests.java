package org.sonarsource.sonarlint.core.telemetry.gessie.event;

import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.sonarsource.sonarlint.core.telemetry.gessie.event.GessieMetadata.SonarLintDomain;

class SonarLintDomainTests {

  @ParameterizedTest
  @MethodSource
  void should_map_product_key_to_domain(String productKey, SonarLintDomain expected) {
    var actual = SonarLintDomain.fromProductKey(productKey);

    assertThat(actual).isEqualTo(expected);
  }

  public static Stream<Arguments> should_map_product_key_to_domain() {
    return Stream.of(
      Arguments.of("idea", SonarLintDomain.INTELLIJ),
      Arguments.of("eclipse", SonarLintDomain.ECLIPSE),
      Arguments.of("visualstudio", SonarLintDomain.VISUAL_STUDIO),
      Arguments.of("vscode", SonarLintDomain.VS_CODE),
      Arguments.of("cursor", SonarLintDomain.VS_CODE),
      Arguments.of("windsurf", SonarLintDomain.VS_CODE),
      Arguments.of("", SonarLintDomain.SLCORE),
      Arguments.of("test", SonarLintDomain.SLCORE)
    );
  }
}
