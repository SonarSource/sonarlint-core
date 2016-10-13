package org.sonarsource.sonarlint.core.container.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.Before;
import org.junit.Test;
import org.sonarsource.sonarlint.core.proto.Sonarlint.ModuleList;
import org.sonarsource.sonarlint.core.proto.Sonarlint.ModuleList.Module;

public class AllModulesReaderTest {
  private StorageManager storageManager;

  @Before
  public void setUp() {
    storageManager = mock(StorageManager.class);
  }

  @Test
  public void should_get_modules() {
    ModuleList.Builder list = ModuleList.newBuilder();
    Module m1 = Module.newBuilder().setKey("module1").build();
    list.getMutableModulesByKey().put("module1", m1);

    when(storageManager.readModuleListFromStorage()).thenReturn(list.build());

    AllModulesReader modulesReader = new AllModulesReader(storageManager);
    assertThat(modulesReader.get()).containsOnlyKeys("module1");
  }
}
