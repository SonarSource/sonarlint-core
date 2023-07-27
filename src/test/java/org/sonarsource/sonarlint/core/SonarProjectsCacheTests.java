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
package org.sonarsource.sonarlint.core;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.sonarsource.sonarlint.core.event.ConnectionConfigurationRemovedEvent;
import org.sonarsource.sonarlint.core.event.ConnectionConfigurationUpdatedEvent;
import org.sonarsource.sonarlint.core.serverapi.ServerApi;
import org.sonarsource.sonarlint.core.serverapi.component.ServerProject;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SonarProjectsCacheTests {

  public static final String SQ_1 = "sq1";
  public static final String PROJECT_KEY_1 = "projectKey1";
  public static final String PROJECT_KEY_2 = "projectKey2";
  public static final String PROJECT_NAME_1 = "Project 1";
  public static final String PROJECT_NAME_2 = "Project 2";
  public static final ServerProject PROJECT_1 = new ServerProject() {
    @Override
    public String getKey() {
      return PROJECT_KEY_1;
    }

    @Override
    public String getName() {
      return PROJECT_NAME_1;
    }
  };
  public static final ServerProject PROJECT_1_CHANGED = new ServerProject() {
    @Override
    public String getKey() {
      return PROJECT_KEY_1;
    }

    @Override
    public String getName() {
      return PROJECT_NAME_2;
    }
  };
  public static final ServerProject PROJECT_2 = new ServerProject() {
    @Override
    public String getKey() {
      return PROJECT_KEY_2;
    }

    @Override
    public String getName() {
      return PROJECT_NAME_2;
    }
  };
  private final ServerApiProvider serverApiProvider = mock(ServerApiProvider.class);
  private final ServerApi serverApi = mock(ServerApi.class, Mockito.RETURNS_DEEP_STUBS);
  private final SonarProjectsCache underTest = new SonarProjectsCache(serverApiProvider);

  @BeforeEach
  public void setup() {
    when(serverApiProvider.getServerApi(SQ_1)).thenReturn(Optional.of(serverApi));
  }

  @Test
  void getSonarProject_should_query_server_once() {
    when(serverApi.component().getProject(PROJECT_KEY_1))
      .thenReturn(Optional.of(PROJECT_1))
      .thenThrow(new AssertionError("Should only be called once"));

    var sonarProjectCall1 = underTest.getSonarProject(SQ_1, PROJECT_KEY_1);

    assertThat(sonarProjectCall1).isPresent();
    assertThat(sonarProjectCall1.get().getKey()).isEqualTo(PROJECT_KEY_1);
    assertThat(sonarProjectCall1.get().getName()).isEqualTo(PROJECT_NAME_1);

    var sonarProjectCall2 = underTest.getSonarProject(SQ_1, PROJECT_KEY_1);

    assertThat(sonarProjectCall2).isPresent();
    assertThat(sonarProjectCall2.get().getKey()).isEqualTo(PROJECT_KEY_1);
    assertThat(sonarProjectCall2.get().getName()).isEqualTo(PROJECT_NAME_1);

    verify(serverApi.component(), times(1)).getProject(PROJECT_KEY_1);
  }

  @Test
  void getSonarProject_should_cache_failure() {
    when(serverApi.component().getProject(PROJECT_KEY_1))
      .thenThrow(new RuntimeException("Unable to fetch project"))
      .thenReturn(Optional.of(PROJECT_1));

    var sonarProjectCall1 = underTest.getSonarProject(SQ_1, PROJECT_KEY_1);

    assertThat(sonarProjectCall1).isEmpty();

    var sonarProjectCall2 = underTest.getSonarProject(SQ_1, PROJECT_KEY_1);

    assertThat(sonarProjectCall2).isEmpty();

    verify(serverApi.component(), times(1)).getProject(PROJECT_KEY_1);
  }

  @Test
  void evict_cache_if_connection_removed_to_save_memory() {
    when(serverApi.component().getProject(PROJECT_KEY_1))
      .thenReturn(Optional.of(PROJECT_1));

    var sonarProjectCall1 = underTest.getSonarProject(SQ_1, PROJECT_KEY_1);

    assertThat(sonarProjectCall1).isPresent();
    assertThat(sonarProjectCall1.get().getKey()).isEqualTo(PROJECT_KEY_1);
    assertThat(sonarProjectCall1.get().getName()).isEqualTo(PROJECT_NAME_1);

    underTest.connectionRemoved(new ConnectionConfigurationRemovedEvent(SQ_1));

    var sonarProjectCall2 = underTest.getSonarProject(SQ_1, PROJECT_KEY_1);

    assertThat(sonarProjectCall2).isPresent();
    assertThat(sonarProjectCall2.get().getKey()).isEqualTo(PROJECT_KEY_1);
    assertThat(sonarProjectCall2.get().getName()).isEqualTo(PROJECT_NAME_1);

    verify(serverApi.component(), times(2)).getProject(PROJECT_KEY_1);
  }

  @Test
  void evict_cache_if_connection_updated_to_refresh_on_next_get() {
    when(serverApi.component().getProject(PROJECT_KEY_1))
      .thenReturn(Optional.of(PROJECT_1))
      .thenReturn(Optional.of(PROJECT_1_CHANGED));

    var sonarProjectCall1 = underTest.getSonarProject(SQ_1, PROJECT_KEY_1);

    assertThat(sonarProjectCall1).isPresent();
    assertThat(sonarProjectCall1.get().getKey()).isEqualTo(PROJECT_KEY_1);
    assertThat(sonarProjectCall1.get().getName()).isEqualTo(PROJECT_NAME_1);

    underTest.connectionUpdated(new ConnectionConfigurationUpdatedEvent(SQ_1));

    var sonarProjectCall2 = underTest.getSonarProject(SQ_1, PROJECT_KEY_1);

    assertThat(sonarProjectCall2).isPresent();
    assertThat(sonarProjectCall2.get().getKey()).isEqualTo(PROJECT_KEY_1);
    assertThat(sonarProjectCall2.get().getName()).isEqualTo(PROJECT_NAME_2);

    verify(serverApi.component(), times(2)).getProject(PROJECT_KEY_1);
  }

  @Test
  void getTextSearchIndex_should_query_server_once() {
    when(serverApi.component().getAllProjects(any()))
      .thenReturn(List.of(PROJECT_1, PROJECT_2))
      .thenThrow(new AssertionError("Should only be called once"));

    var searchIndex1 = underTest.getTextSearchIndex(SQ_1);

    assertThat(searchIndex1.size()).isEqualTo(2);

    var searchIndex2 = underTest.getTextSearchIndex(SQ_1);

    assertThat(searchIndex2.size()).isEqualTo(2);

    verify(serverApi.component(), times(1)).getAllProjects(any());
  }

  @Test
  void getTextSearchIndex_should_return_empty_index_if_no_projects() {
    when(serverApi.component().getAllProjects(any()))
      .thenReturn(List.of())
      .thenThrow(new AssertionError("Should only be called once"));

    var searchIndex1 = underTest.getTextSearchIndex(SQ_1);

    assertThat(searchIndex1.isEmpty()).isTrue();

    var searchIndex2 = underTest.getTextSearchIndex(SQ_1);

    assertThat(searchIndex1.isEmpty()).isTrue();

    verify(serverApi.component(), times(1)).getAllProjects(any());
  }

  @Test
  void getTextSearchIndex_should_cache_failure() {
    when(serverApi.component().getAllProjects(any()))
      .thenThrow(new RuntimeException("Unable to fetch projects"))
      .thenReturn(List.of(PROJECT_1, PROJECT_2));

    var searchIndex1 = underTest.getTextSearchIndex(SQ_1);

    assertThat(searchIndex1.isEmpty()).isTrue();

    var searchIndex2 = underTest.getTextSearchIndex(SQ_1);

    assertThat(searchIndex1.isEmpty()).isTrue();

    verify(serverApi.component(), times(1)).getAllProjects(any());
  }
}
