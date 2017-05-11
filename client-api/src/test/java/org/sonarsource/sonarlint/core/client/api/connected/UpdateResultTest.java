package org.sonarsource.sonarlint.core.client.api.connected;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.util.Collection;

import org.junit.Test;

public class UpdateResultTest {
  @Test
  public void testRoundTrip() {
    GlobalStorageStatus status = mock(GlobalStorageStatus.class);
    Collection<SonarAnalyzer> analyzers = mock(Collection.class);
    UpdateResult result = new UpdateResult(status, analyzers);
    assertThat(result.analyzers()).isEqualTo(analyzers);
    assertThat(result.status()).isEqualTo(status);
  }
}
