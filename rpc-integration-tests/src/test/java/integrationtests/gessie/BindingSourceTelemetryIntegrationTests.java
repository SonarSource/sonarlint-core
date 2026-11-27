/*
 * SonarLint Core - RPC Integration Tests
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
package integrationtests.gessie;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.eclipse.jgit.api.Git;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;
import org.sonarsource.sonarlint.core.rpc.protocol.client.telemetry.BindingSource;
import org.sonarsource.sonarlint.core.rpc.protocol.client.telemetry.BindingSourcesObservedParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.telemetry.ObservedBindingSourceDto;
import org.sonarsource.sonarlint.core.telemetry.gessie.GessieSpringConfig;
import org.sonarsource.sonarlint.core.test.utils.junit5.SonarLintTest;
import org.sonarsource.sonarlint.core.test.utils.junit5.SonarLintTestHarness;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

class BindingSourceTelemetryIntegrationTests {
  private static final String EVENT_TYPE = "Analytics.Editor.IdeBindingSourceObserved";

  @RegisterExtension
  static WireMockExtension endpoint = WireMockExtension.newInstance().options(wireMockConfig().dynamicPort()).build();

  @TempDir
  Path workspace;

  @BeforeEach
  void setUp() {
    System.setProperty(GessieSpringConfig.PROPERTY_GESSIE_API_KEY, "value");
    endpoint.stubFor(post("/ide").willReturn(aResponse().withStatus(202)));
  }

  @AfterEach
  void tearDown() {
    System.clearProperty(GessieSpringConfig.PROPERTY_GESSIE_API_KEY);
    System.clearProperty(GessieSpringConfig.PROPERTY_GESSIE_ENDPOINT);
  }

  @SonarLintTest
  void should_post_one_event_per_declaration_with_a_shared_snapshot_id_and_the_repository_of_its_root(SonarLintTestHarness harness) throws Exception {
    var repo = Files.createDirectories(workspace.resolve("repo"));
    Git.init().setDirectory(repo.toFile()).call().close();
    var backend = Files.createDirectories(repo.resolve("packages/backend"));
    var scratch = Files.createDirectories(workspace.resolve("scratch"));
    var backendInstance = harness.newBackend().withProductKey("vscode").withGessieTelemetryEnabled(endpoint.baseUrl()).start();

    backendInstance.getTelemetryService().bindingSourcesObserved(new BindingSourcesObservedParams(List.of(
      new ObservedBindingSourceDto(repo, BindingSource.SONAR_CONFIG, 3),
      new ObservedBindingSourceDto(repo, BindingSource.SONAR_CONFIG, 3),
      new ObservedBindingSourceDto(backend, BindingSource.IDE_SETTINGS, 1),
      new ObservedBindingSourceDto(scratch, BindingSource.IDE_SETTINGS, 1))));

    await().untilAsserted(() -> assertThat(bindingSourceEvents()).hasSize(3));
    var payloads = bindingSourceEvents().stream().map(event -> {
      var metadata = event.getAsJsonObject("metadata");
      assertThat(metadata.get("event_version").getAsString()).isEqualTo("1");
      assertThat(metadata.get("source")).isEqualTo(JsonParser.parseString("{\"domain\":\"VSCode\"}"));
      var payload = event.getAsJsonObject("event_payload");
      assertThat(payload.get("machine_id").getAsString()).isNotBlank();
      assertThat(payload.get("ide_installation_id").getAsString()).isEqualTo(backendInstance.telemetryFileContent().ideInstallationId());
      payload.remove("machine_id");
      payload.remove("ide_installation_id");
      return payload;
    }).toList();
    var snapshotId = payloads.get(0).get("snapshot_id").getAsString();
    var repositoryId = payloads.stream().filter(payload -> payload.get("binding_source").getAsString().equals("SONAR_CONFIG")).findFirst().orElseThrow()
      .get("repository_id").getAsString();
    assertThat(UUID.fromString(snapshotId)).isNotNull();
    assertThat(UUID.fromString(repositoryId)).isNotNull();
    assertThat(payloads).containsExactlyInAnyOrder(
      json("""
        {"snapshot_id":"%s","repository_id":"%s","binding_source":"SONAR_CONFIG","declared_binding_count":3}
        """.formatted(snapshotId, repositoryId)),
      json("""
        {"snapshot_id":"%s","repository_id":"%s","binding_source":"IDE_SETTINGS","declared_binding_count":1}
        """.formatted(snapshotId, repositoryId)),
      json("""
        {"snapshot_id":"%s","repository_id":null,"binding_source":"IDE_SETTINGS","declared_binding_count":1}
        """.formatted(snapshotId)));
  }

  @SonarLintTest
  void should_post_a_new_snapshot_id_for_each_call(SonarLintTestHarness harness) {
    var telemetry = harness.newBackend().withGessieTelemetryEnabled(endpoint.baseUrl()).start().getTelemetryService();

    telemetry.bindingSourcesObserved(settingsSource());
    telemetry.bindingSourcesObserved(settingsSource());

    await().untilAsserted(() -> assertThat(bindingSourceEvents()).hasSize(2));
    assertThat(bindingSourceEvents()).extracting(event -> event.getAsJsonObject("event_payload").get("snapshot_id").getAsString()).doesNotHaveDuplicates();
  }

  @SonarLintTest
  void should_not_post_binding_sources_without_gessie_capability(SonarLintTestHarness harness) {
    System.setProperty(GessieSpringConfig.PROPERTY_GESSIE_ENDPOINT, endpoint.baseUrl());
    var telemetry = harness.newBackend().start().getTelemetryService();

    telemetry.bindingSourcesObserved(settingsSource());

    await().during(Duration.ofMillis(200)).untilAsserted(() -> assertThat(bindingSourceEvents()).isEmpty());
  }

  @SonarLintTest
  void should_not_post_binding_sources_without_user_consent(SonarLintTestHarness harness) {
    var telemetry = harness.newBackend().withGessieTelemetryEnabled(endpoint.baseUrl()).start().getTelemetryService();
    telemetry.disableTelemetry();
    assertThat(telemetry.getStatus().join().isEnabled()).isFalse();

    telemetry.bindingSourcesObserved(settingsSource());

    await().during(Duration.ofMillis(200)).untilAsserted(() -> assertThat(bindingSourceEvents()).isEmpty());
  }

  private static BindingSourcesObservedParams settingsSource() {
    return new BindingSourcesObservedParams(List.of(new ObservedBindingSourceDto(null, BindingSource.IDE_SETTINGS, 1)));
  }

  private static JsonObject json(String content) {
    return JsonParser.parseString(content).getAsJsonObject();
  }

  private static List<JsonObject> bindingSourceEvents() {
    return endpoint.getAllServeEvents().stream()
      .map(event -> JsonParser.parseString(event.getRequest().getBodyAsString()).getAsJsonObject())
      .filter(event -> event.getAsJsonObject("metadata").get("event_type").getAsString().equals(EVENT_TYPE))
      .toList();
  }
}
