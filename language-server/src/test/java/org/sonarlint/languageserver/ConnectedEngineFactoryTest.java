package org.sonarlint.languageserver;

import com.google.common.collect.ImmutableMap;
import java.util.Collections;
import java.util.Map;
import org.junit.*;
import org.mockito.ArgumentCaptor;
import org.sonarsource.sonarlint.core.ConnectedSonarLintEngineImpl;
import org.sonarsource.sonarlint.core.client.api.common.LogOutput;
import org.sonarsource.sonarlint.core.client.api.connected.ConnectedGlobalConfiguration;
import org.sonarsource.sonarlint.core.client.api.connected.ConnectedSonarLintEngine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

public class ConnectedEngineFactoryTest {
  @Test
  public void create_returns_engine_with_configured_properties() {
    ConnectedEngineFactoryTester tester = new ConnectedEngineFactoryTester();

    Map<String, String> extraProperties = ImmutableMap.<String, String>builder()
      .put("foo", "bar")
      .put("baz", "abc")
      .build();
    extraProperties.forEach(tester::putExtraProperty);

    String serverId = "local1";
    ServerInfo serverInfo = new ServerInfo(serverId, "http://localhost:9000", "dummy token", null);
    ConnectedSonarLintEngine engine = tester.create(serverInfo);

    assertThat(tester.configuration.getServerId()).isEqualTo(serverId);
    assertThat(tester.configuration.extraProperties()).isEqualTo(extraProperties);
  }

  @Test
  public void create_returns_null_on_runtime_exception() {
    ConnectedEngineFactory tester = new ConnectedEngineFactoryTester() {
      @Override
      ConnectedSonarLintEngine createEngine(ConnectedGlobalConfiguration configuration) {
        throw new IllegalStateException("boom");
      }
    };

    assertThat(tester.create(mock(ServerInfo.class))).isNull();
  }

  static class ConnectedEngineFactoryTester extends ConnectedEngineFactory {
    private ConnectedGlobalConfiguration configuration;

    ConnectedEngineFactoryTester() {
      super(mock(LogOutput.class), mock(ClientLogger.class));
    }

    @Override
    ConnectedSonarLintEngine createEngine(ConnectedGlobalConfiguration configuration) {
      this.configuration = configuration;
      return mock(ConnectedSonarLintEngine.class);
    }
  }
}
