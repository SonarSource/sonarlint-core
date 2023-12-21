package org.sonarsource.sonarlint.core.file;

import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sonarsource.sonarlint.core.ServerApiProvider;
import org.sonarsource.sonarlint.core.fs.ClientFileSystemService;
import org.sonarsource.sonarlint.core.repository.config.ConfigurationRepository;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PathTranslationServiceTests {

  private PathTranslationService underTest;
  private ConfigurationRepository configurationRepository;

  @BeforeEach
  void prepare() {
    configurationRepository = mock(ConfigurationRepository.class);
    underTest = new PathTranslationService(mock(ClientFileSystemService.class), mock(ServerApiProvider.class), configurationRepository);

  }

  @Test
  void shouldRethrowOnExecutionException() {
    when(configurationRepository.getBoundScope(anyString())).thenThrow(new CancellationException());

    assertThrows(IllegalStateException.class, () -> underTest.getOrComputePathTranslation("scope"));
  }

}
