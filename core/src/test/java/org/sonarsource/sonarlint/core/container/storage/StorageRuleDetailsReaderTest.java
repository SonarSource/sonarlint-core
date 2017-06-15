/*
 * SonarLint Core - Implementation
 * Copyright (C) 2009-2017 SonarSource SA
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
  private StorageReader storageReader;

  @Rule
  public ExpectedException exception = ExpectedException.none();

  @Before
  public void setUp() {
    storageReader = mock(StorageReader.class);
  }

  @Test
  public void should_find_key() {
    Rules.Builder rules = Rules.newBuilder();
    rules.getMutableRulesByKey().put("repo:key1", Rules.Rule.newBuilder().setKey("repo:key1").build());
    when(storageReader.readRules()).thenReturn(rules.build());

    StorageRuleDetailsReader ruleReader = new StorageRuleDetailsReader(storageReader);
    assertThat(ruleReader.apply("repo:key1")).isNotNull();
  }

  @Test
  public void should_throw_error_if_key_not_found() {
    Rules.Builder rules = Rules.newBuilder();
    rules.getMutableRulesByKey().put("repo:key1", Rules.Rule.newBuilder().setKey("repo:key1").build());
    when(storageReader.readRules()).thenReturn(rules.build());

    StorageRuleDetailsReader ruleReader = new StorageRuleDetailsReader(storageReader);

    exception.expect(IllegalArgumentException.class);
    exception.expectMessage("Unable to find rule");
    ruleReader.apply("repo:key2");
  }
}
