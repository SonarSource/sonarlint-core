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
package org.sonarsource.sonarlint.core.telemetry;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import org.eclipse.jgit.api.Git;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sonarsource.sonarlint.core.rpc.protocol.client.telemetry.BindingSource;
import org.sonarsource.sonarlint.core.rpc.protocol.client.telemetry.BindingSourcesObservedParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.telemetry.ObservedBindingSourceDto;
import org.sonarsource.sonarlint.core.telemetry.gessie.event.payload.BindingSourceObservedPayload;

import static org.assertj.core.api.Assertions.assertThat;

class BindingSourceTelemetryServiceTests {

  @TempDir
  Path workspace;

  private final BindingSourceTelemetryService underTest = new BindingSourceTelemetryService();

  @Test
  void should_produce_one_event_per_declaration_with_a_shared_snapshot_id_and_the_repository_of_its_root() throws Exception {
    var repo = repo("repo");
    var frontend = Files.createDirectories(repo.resolve("packages/frontend"));
    var scratch = Files.createDirectories(workspace.resolve("scratch"));

    var events = underTest.toEvents(params(
      new ObservedBindingSourceDto(repo, BindingSource.SONAR_CONFIG, 3),
      new ObservedBindingSourceDto(frontend, BindingSource.IDE_SETTINGS, 1),
      new ObservedBindingSourceDto(scratch, BindingSource.IDE_SETTINGS, 1)));

    var snapshotId = events.get(0).snapshotId();
    var repositoryId = events.get(0).repositoryId();
    assertThat(UUID.fromString(snapshotId)).isNotNull();
    assertThat(UUID.fromString(repositoryId)).isNotNull();
    assertThat(events).containsExactly(
      new BindingSourceObservedPayload(snapshotId, repositoryId, BindingSourceObservedPayload.BindingSource.SONAR_CONFIG, 3),
      new BindingSourceObservedPayload(snapshotId, repositoryId, BindingSourceObservedPayload.BindingSource.IDE_SETTINGS, 1),
      new BindingSourceObservedPayload(snapshotId, null, BindingSourceObservedPayload.BindingSource.IDE_SETTINGS, 1));
  }

  @Test
  void should_merge_the_sources_of_the_same_kind_declared_at_the_same_root() throws Exception {
    var repo = repo("repo");

    var events = underTest.toEvents(params(
      new ObservedBindingSourceDto(repo, BindingSource.SONAR_CONFIG, 2),
      new ObservedBindingSourceDto(repo.resolve("packages").resolve(".."), BindingSource.SONAR_CONFIG, 2),
      new ObservedBindingSourceDto(repo, BindingSource.IDE_SETTINGS, 1)));

    assertThat(events).extracting(BindingSourceObservedPayload::bindingSource)
      .containsExactly(BindingSourceObservedPayload.BindingSource.SONAR_CONFIG, BindingSourceObservedPayload.BindingSource.IDE_SETTINGS);
  }

  @Test
  void should_keep_every_source_without_a_root() {
    var events = underTest.toEvents(params(
      new ObservedBindingSourceDto(null, BindingSource.IDE_SETTINGS, 1),
      new ObservedBindingSourceDto(null, BindingSource.IDE_SETTINGS, 1)));

    assertThat(events).hasSize(2).extracting(BindingSourceObservedPayload::repositoryId).containsOnlyNulls();
  }

  @Test
  void should_generate_a_new_snapshot_id_for_each_report_and_keep_the_repository_id() throws Exception {
    var repo = repo("repo");

    var first = underTest.toEvents(params(new ObservedBindingSourceDto(repo, BindingSource.IDE_SETTINGS, 1))).get(0);
    var second = underTest.toEvents(params(new ObservedBindingSourceDto(repo, BindingSource.IDE_SETTINGS, 1))).get(0);

    assertThat(first.snapshotId()).isNotEqualTo(second.snapshotId());
    assertThat(first.repositoryId()).isNotNull().isEqualTo(second.repositoryId());
  }

  @Test
  void should_give_different_repositories_different_ids_and_a_new_id_in_a_new_session() throws Exception {
    var x = repo("x");
    var z = repo("z");

    var events = underTest.toEvents(params(
      new ObservedBindingSourceDto(x, BindingSource.IDE_SETTINGS, 1),
      new ObservedBindingSourceDto(z, BindingSource.IDE_SETTINGS, 1)));
    var nextSession = new BindingSourceTelemetryService().toEvents(params(new ObservedBindingSourceDto(x, BindingSource.IDE_SETTINGS, 1)));

    assertThat(Stream.concat(events.stream(), nextSession.stream()).map(BindingSourceObservedPayload::repositoryId))
      .hasSize(3).doesNotContainNull().doesNotHaveDuplicates();
  }

  @Test
  void should_drop_invalid_sources_and_keep_the_valid_ones() throws Exception {
    var repo = repo("repo");

    var events = underTest.toEvents(new BindingSourcesObservedParams(Arrays.asList(
      null,
      new ObservedBindingSourceDto(repo, null, 1),
      new ObservedBindingSourceDto(repo, BindingSource.SONAR_CONFIG, 0),
      new ObservedBindingSourceDto(repo, BindingSource.SONAR_CONFIG, 2))));

    assertThat(events).extracting(BindingSourceObservedPayload::declaredBindingCount).containsExactly(2);
  }

  @Test
  void should_produce_no_event_for_a_report_without_sources() {
    assertThat(underTest.toEvents(null)).isEmpty();
    assertThat(underTest.toEvents(new BindingSourcesObservedParams(null))).isEmpty();
  }

  private Path repo(String name) throws Exception {
    var repo = Files.createDirectories(workspace.resolve(name));
    Git.init().setDirectory(repo.toFile()).call().close();
    return repo;
  }

  private static BindingSourcesObservedParams params(ObservedBindingSourceDto... sources) {
    return new BindingSourcesObservedParams(List.of(sources));
  }
}
