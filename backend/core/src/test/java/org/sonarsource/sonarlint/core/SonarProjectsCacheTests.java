/*
 * SonarLint Core - Implementation
 * Copyright (C) SonarSource Sàrl
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
import java.util.function.Function;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.mockito.Mockito;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogTester;
import org.sonarsource.sonarlint.core.commons.progress.SonarLintCancelMonitor;
import org.sonarsource.sonarlint.core.connection.SonarQubeClient;
import org.sonarsource.sonarlint.core.event.ConnectionConfigurationRemovedEvent;
import org.sonarsource.sonarlint.core.event.ConnectionConfigurationUpdatedEvent;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.connection.projects.SonarProjectDto;
import org.sonarsource.sonarlint.core.serverapi.ServerApi;
import org.sonarsource.sonarlint.core.serverapi.component.ServerProject;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class SonarProjectsCacheTests {
  @RegisterExtension
  private static final SonarLintLogTester logTester = new SonarLintLogTester();

  public static final String SQ_1 = "sq1";
  public static final String PROJECT_KEY_1 = "projectKey1";
  public static final String PROJECT_KEY_2 = "projectKey2";
  public static final String PROJECT_NAME_1 = "Project 1";
  public static final String PROJECT_NAME_2 = "Project 2";
  public static final ServerProject PROJECT_1 = new ServerProject(PROJECT_KEY_1, PROJECT_NAME_1, false);
  public static final ServerProject PROJECT_1_CHANGED = new ServerProject(PROJECT_KEY_1, PROJECT_NAME_2, false);
  public static final ServerProject PROJECT_2 = new ServerProject(PROJECT_KEY_2, PROJECT_NAME_2, false);
  private final ServerApi serverApi = mock(ServerApi.class, Mockito.RETURNS_DEEP_STUBS);
  private final SonarQubeClientManager sonarQubeClientManager = mock(SonarQubeClientManager.class);
  private final SonarQubeClient sonarQubeClient = mock(SonarQubeClient.class);
  private final SonarProjectsCache underTest = new SonarProjectsCache(sonarQubeClientManager);

  @BeforeEach
  void setup() {
    when(sonarQubeClientManager.withActiveClientAndReturn(any(), any())).thenAnswer(
      invocation -> Optional.ofNullable(((Function<ServerApi, Object>) invocation.getArguments()[1]).apply(serverApi)));
    when(sonarQubeClientManager.getValidClientOrThrow(any())).thenReturn(sonarQubeClient);
    when(sonarQubeClient.withClientApiAndReturnThrowing(any())).thenAnswer(
      invocation -> ((Function<ServerApi, Object>) invocation.getArguments()[0]).apply(serverApi));
  }

  @Test
  void getSonarProject_should_query_server_once() {
    when(serverApi.component().getProject(eq(PROJECT_KEY_1), any(SonarLintCancelMonitor.class)))
      .thenReturn(Optional.of(PROJECT_1))
      .thenThrow(new AssertionError("Should only be called once"));

    var sonarProjectCall1 = underTest.getSonarProject(SQ_1, PROJECT_KEY_1, new SonarLintCancelMonitor());

    assertThat(sonarProjectCall1).isPresent();
    assertThat(sonarProjectCall1.get().key()).isEqualTo(PROJECT_KEY_1);
    assertThat(sonarProjectCall1.get().name()).isEqualTo(PROJECT_NAME_1);

    var sonarProjectCall2 = underTest.getSonarProject(SQ_1, PROJECT_KEY_1, new SonarLintCancelMonitor());

    assertThat(sonarProjectCall2).isPresent();
    assertThat(sonarProjectCall2.get().key()).isEqualTo(PROJECT_KEY_1);
    assertThat(sonarProjectCall2.get().name()).isEqualTo(PROJECT_NAME_1);
    verify(serverApi.component(), times(1)).getProject(eq(PROJECT_KEY_1), any(SonarLintCancelMonitor.class));
  }

  @Test
  void getSonarProject_should_cache_failure() {
    when(serverApi.component().getProject(eq(PROJECT_KEY_1), any(SonarLintCancelMonitor.class)))
      .thenThrow(new RuntimeException("Unable to fetch project"))
      .thenReturn(Optional.of(PROJECT_1));

    var sonarProjectCall1 = underTest.getSonarProject(SQ_1, PROJECT_KEY_1, new SonarLintCancelMonitor());

    assertThat(sonarProjectCall1).isEmpty();

    var sonarProjectCall2 = underTest.getSonarProject(SQ_1, PROJECT_KEY_1, new SonarLintCancelMonitor());

    assertThat(sonarProjectCall2).isEmpty();
    verify(serverApi.component(), times(1)).getProject(eq(PROJECT_KEY_1), any(SonarLintCancelMonitor.class));
  }

  @Test
  void evict_cache_if_connection_removed_to_save_memory() {
    when(serverApi.component().getProject(eq(PROJECT_KEY_1), any(SonarLintCancelMonitor.class)))
      .thenReturn(Optional.of(PROJECT_1));

    var sonarProjectCall1 = underTest.getSonarProject(SQ_1, PROJECT_KEY_1, new SonarLintCancelMonitor());

    assertThat(sonarProjectCall1).isPresent();
    assertThat(sonarProjectCall1.get().key()).isEqualTo(PROJECT_KEY_1);
    assertThat(sonarProjectCall1.get().name()).isEqualTo(PROJECT_NAME_1);

    underTest.connectionRemoved(new ConnectionConfigurationRemovedEvent(SQ_1));

    var sonarProjectCall2 = underTest.getSonarProject(SQ_1, PROJECT_KEY_1, new SonarLintCancelMonitor());

    assertThat(sonarProjectCall2).isPresent();
    assertThat(sonarProjectCall2.get().key()).isEqualTo(PROJECT_KEY_1);
    assertThat(sonarProjectCall2.get().name()).isEqualTo(PROJECT_NAME_1);
    verify(serverApi.component(), times(2)).getProject(eq(PROJECT_KEY_1), any(SonarLintCancelMonitor.class));
  }

  @Test
  void evict_cache_if_connection_updated_to_refresh_on_next_get() {
    when(serverApi.component().getProject(eq(PROJECT_KEY_1), any(SonarLintCancelMonitor.class)))
      .thenReturn(Optional.of(PROJECT_1))
      .thenReturn(Optional.of(PROJECT_1_CHANGED));

    var sonarProjectCall1 = underTest.getSonarProject(SQ_1, PROJECT_KEY_1, new SonarLintCancelMonitor());

    assertThat(sonarProjectCall1).isPresent();
    assertThat(sonarProjectCall1.get().key()).isEqualTo(PROJECT_KEY_1);
    assertThat(sonarProjectCall1.get().name()).isEqualTo(PROJECT_NAME_1);

    underTest.connectionUpdated(new ConnectionConfigurationUpdatedEvent(SQ_1));

    var sonarProjectCall2 = underTest.getSonarProject(SQ_1, PROJECT_KEY_1, new SonarLintCancelMonitor());

    assertThat(sonarProjectCall2).isPresent();
    assertThat(sonarProjectCall2.get().key()).isEqualTo(PROJECT_KEY_1);
    assertThat(sonarProjectCall2.get().name()).isEqualTo(PROJECT_NAME_2);
    verify(serverApi.component(), times(2)).getProject(eq(PROJECT_KEY_1), any(SonarLintCancelMonitor.class));
  }

  @Test
  void getTextSearchIndex_should_query_server_once() {
    when(serverApi.component().getAllProjects(any()))
      .thenReturn(List.of(PROJECT_1, PROJECT_2))
      .thenThrow(new AssertionError("Should only be called once"));

    var searchIndex1 = underTest.getTextSearchIndex(SQ_1, new SonarLintCancelMonitor());

    assertThat(searchIndex1.size()).isEqualTo(2);

    var searchIndex2 = underTest.getTextSearchIndex(SQ_1, new SonarLintCancelMonitor());

    assertThat(searchIndex2.size()).isEqualTo(2);
    verify(serverApi.component(), times(1)).getAllProjects(any());
  }

  @Test
  void getTextSearchIndex_should_return_empty_index_if_no_projects() {
    when(serverApi.component().getAllProjects(any()))
      .thenReturn(List.of())
      .thenThrow(new AssertionError("Should only be called once"));

    var searchIndex1 = underTest.getTextSearchIndex(SQ_1, new SonarLintCancelMonitor());

    assertThat(searchIndex1.isEmpty()).isTrue();

    underTest.getTextSearchIndex(SQ_1, new SonarLintCancelMonitor());

    assertThat(searchIndex1.isEmpty()).isTrue();
    verify(serverApi.component(), times(1)).getAllProjects(any());
  }

  @Test
  void getTextSearchIndex_should_cache_failure() {
    when(serverApi.component().getAllProjects(any()))
      .thenThrow(new RuntimeException("Unable to fetch projects"))
      .thenReturn(List.of(PROJECT_1, PROJECT_2));

    var searchIndex1 = underTest.getTextSearchIndex(SQ_1, new SonarLintCancelMonitor());

    assertThat(searchIndex1.isEmpty()).isTrue();

    underTest.getTextSearchIndex(SQ_1, new SonarLintCancelMonitor());

    assertThat(searchIndex1.isEmpty()).isTrue();
    verify(serverApi.component(), times(1)).getAllProjects(any());
  }

  @Test
  void fuzzySearchProjects_should_not_query_server_for_blank_search() {
    assertThat(underTest.fuzzySearchProjects(SQ_1, "  ", new SonarLintCancelMonitor())).isEmpty();

    verifyNoInteractions(sonarQubeClientManager);
  }

  @Test
  void fuzzySearchProjects_should_preserve_server_order_promote_exact_key_deduplicate_and_limit_results() {
    var serverResults = new java.util.ArrayList<ServerProject>();
    for (var i = 0; i < 10; i++) {
      serverResults.add(new ServerProject("key" + i, "Project " + i, false));
    }
    var exactProject = new ServerProject("needle", "Exact project", false);
    serverResults.set(4, exactProject);
    serverResults.set(8, exactProject);
    when(serverApi.component().searchProjectsByNameOrKey(eq("needle"), any())).thenReturn(serverResults);

    var actual = underTest.fuzzySearchProjects(SQ_1, " needle ", new SonarLintCancelMonitor());

    assertThat(actual).extracting(SonarProjectDto::getKey)
      .containsExactly("needle", "key0", "key1", "key2", "key3", "key5", "key6", "key7", "key9");
    verify(serverApi.component(), never()).getProjectByExactKey(any(), any());
  }

  @Test
  void fuzzySearchProjects_should_prepend_exact_key_lookup_and_cap_results() {
    var serverResults = java.util.stream.IntStream.range(0, 10)
      .mapToObj(i -> new ServerProject("key" + i, "Project " + i, false))
      .toList();
    when(serverApi.component().searchProjectsByNameOrKey(eq("needle"), any())).thenReturn(serverResults);
    when(serverApi.component().getProjectByExactKey(eq("needle"), any())).thenReturn(Optional.of(new ServerProject("needle", "Exact project", false)));

    var actual = underTest.fuzzySearchProjects(SQ_1, "needle", new SonarLintCancelMonitor());

    assertThat(actual).extracting(SonarProjectDto::getKey)
      .containsExactly("needle", "key0", "key1", "key2", "key3", "key4", "key5", "key6", "key7", "key8");
  }

  @Test
  void fuzzySearchProjects_should_search_by_one_unicode_code_point() {
    when(serverApi.component().searchProjectsByNameOrKey(eq("😀"), any()))
      .thenReturn(List.of(new ServerProject("emoji-project", "😀 project", false)));
    when(serverApi.component().getProjectByExactKey(eq("😀"), any())).thenReturn(Optional.empty());

    var actual = underTest.fuzzySearchProjects(SQ_1, "😀", new SonarLintCancelMonitor());

    assertThat(actual).containsExactly(new SonarProjectDto("emoji-project", "😀 project"));
    verify(serverApi.component()).searchProjectsByNameOrKey(eq("😀"), any());
    verify(serverApi.component()).getProjectByExactKey(eq("😀"), any());
  }

  @Test
  void fuzzySearchProjects_should_not_cache_failures() {
    when(serverApi.component().searchProjectsByNameOrKey(eq("needle"), any()))
      .thenThrow(new RuntimeException("temporary failure"))
      .thenReturn(List.of(new ServerProject("needle", "Project", false)));

    assertThatThrownBy(() -> underTest.fuzzySearchProjects(SQ_1, "needle", new SonarLintCancelMonitor()))
      .isInstanceOf(RuntimeException.class)
      .hasMessage("temporary failure");

    assertThat(underTest.fuzzySearchProjects(SQ_1, "needle", new SonarLintCancelMonitor()))
      .containsExactly(new SonarProjectDto("needle", "Project"));
    verify(serverApi.component(), times(2)).searchProjectsByNameOrKey(eq("needle"), any());
  }

  @Test
  void fuzzySearchProjects_should_translate_authentication_failure() {
    org.mockito.Mockito.doThrow(new org.sonarsource.sonarlint.core.serverapi.exception.UnauthorizedException("401"))
      .when(sonarQubeClient).withClientApiAndReturnThrowing(any());

    assertThatThrownBy(() -> underTest.fuzzySearchProjects(SQ_1, "needle", new SonarLintCancelMonitor()))
      .isInstanceOf(org.eclipse.lsp4j.jsonrpc.ResponseErrorException.class)
      .satisfies(error -> assertThat(((org.eclipse.lsp4j.jsonrpc.ResponseErrorException) error).getResponseError().getCode())
        .isEqualTo(org.sonarsource.sonarlint.core.rpc.protocol.SonarLintRpcErrorCode.UNAUTHORIZED));
  }

  @Test
  void fuzzySearchProjects_should_stop_before_exact_lookup_when_cancelled() {
    var cancelMonitor = new SonarLintCancelMonitor();
    when(serverApi.component().searchProjectsByNameOrKey(eq("needle"), any())).thenAnswer(invocation -> {
      cancelMonitor.cancel();
      return List.of();
    });

    assertThatThrownBy(() -> underTest.fuzzySearchProjects(SQ_1, "needle", cancelMonitor))
      .isInstanceOf(java.util.concurrent.CancellationException.class);
    verify(serverApi.component(), never()).getProjectByExactKey(any(), any());
  }
}
