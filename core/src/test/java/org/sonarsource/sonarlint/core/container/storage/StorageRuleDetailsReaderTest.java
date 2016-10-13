package org.sonarsource.sonarlint.core.container.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.sonarsource.sonarlint.core.proto.Sonarlint.Rules;

public class StorageRuleDetailsReaderTest {
  private StorageManager storageManager;

  @Rule
  public ExpectedException exception = ExpectedException.none();

  @Before
  public void setUp() {
    storageManager = mock(StorageManager.class);
  }

  @Test
  public void should_find_key() {
    Rules.Builder rules = Rules.newBuilder();
    rules.getMutableRulesByKey().put("repo:key1", Rules.Rule.newBuilder().setKey("repo:key1").build());
    when(storageManager.readRulesFromStorage()).thenReturn(rules.build());

    StorageRuleDetailsReader ruleReader = new StorageRuleDetailsReader(storageManager);
    assertThat(ruleReader.apply("repo:key1")).isNotNull();
  }

  @Test
  public void should_throw_error_if_key_not_found() {
    Rules.Builder rules = Rules.newBuilder();
    rules.getMutableRulesByKey().put("repo:key1", Rules.Rule.newBuilder().setKey("repo:key1").build());
    when(storageManager.readRulesFromStorage()).thenReturn(rules.build());

    StorageRuleDetailsReader ruleReader = new StorageRuleDetailsReader(storageManager);
    
    exception.expect(IllegalArgumentException.class);
    exception.expectMessage("Unable to find rule");
    ruleReader.apply("repo:key2");
  }
}
