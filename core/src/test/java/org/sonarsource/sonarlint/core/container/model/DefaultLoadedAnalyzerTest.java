package org.sonarsource.sonarlint.core.container.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.Test;

public class DefaultLoadedAnalyzerTest {
  @Test
  public void testRoundTrip() {
    DefaultLoadedAnalyzer analyzer = new DefaultLoadedAnalyzer("key", "name", "version");
    assertThat(analyzer.key()).isEqualTo("key");
    assertThat(analyzer.name()).isEqualTo("name");
    assertThat(analyzer.version()).isEqualTo("version");
  }
}
