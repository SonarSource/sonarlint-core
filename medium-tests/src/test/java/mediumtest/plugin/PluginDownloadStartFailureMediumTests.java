/*
 * SonarLint Core - Medium Tests
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
package mediumtest.plugin;

import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import org.eclipse.lsp4j.jsonrpc.ResponseErrorException;
import org.sonarsource.sonarlint.core.plugin.PluginStatusesChangedEvent;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.plugin.GetPluginStatusesParams;
import org.sonarsource.sonarlint.core.rpc.protocol.common.Language;
import org.sonarsource.sonarlint.core.test.utils.SonarLintTestRpcServer;
import org.sonarsource.sonarlint.core.test.utils.junit5.SonarLintTest;
import org.sonarsource.sonarlint.core.test.utils.junit5.SonarLintTestHarness;
import org.springframework.context.ApplicationListener;
import org.springframework.context.PayloadApplicationEvent;
import org.springframework.context.event.SimpleApplicationEventMulticaster;
import org.springframework.scheduling.support.TaskUtils;
import org.springframework.util.ErrorHandler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PluginDownloadStartFailureMediumTests {

  @SonarLintTest
  void should_not_keep_downloading_status_when_publication_and_download_start_fail(SonarLintTestHarness harness) {
    var backend = harness.newBackend()
      .withEnabledLanguageInStandaloneMode(Language.CPP)
      .start();
    var context = backend.getApplicationContext();
    var multicaster = context.getBean(SimpleApplicationEventMulticaster.class);
    multicaster.setErrorHandler(rethrowingErrorHandler());
    context.addApplicationListener(statusPublicationFailure());
    context.getBean("pluginDownloadExecutor", ExecutorService.class).shutdown();

    try {
      assertPublicationAndStartBothFailed(backend);
      // A cached configuration would answer this second call without publishing again.
      assertPublicationAndStartBothFailed(backend);
    } finally {
      multicaster.setErrorHandler(TaskUtils.LOG_AND_SUPPRESS_ERROR_HANDLER);
    }
  }

  private static void assertPublicationAndStartBothFailed(SonarLintTestRpcServer backend) {
    assertThatThrownBy(() -> backend.getPluginService().getPluginStatuses(new GetPluginStatusesParams(null)).join())
      .isInstanceOf(CompletionException.class)
      .cause()
      .isInstanceOfSatisfying(ResponseErrorException.class, error -> assertThat(error.getResponseError().getData().toString())
        .contains("status listener failed")
        .contains("Suppressed: java.util.concurrent.RejectedExecutionException"));
  }

  private static ErrorHandler rethrowingErrorHandler() {
    return error -> {
      if (error instanceof RuntimeException runtimeException) {
        throw runtimeException;
      }
      throw new IllegalStateException(error);
    };
  }

  private static ApplicationListener<PayloadApplicationEvent<?>> statusPublicationFailure() {
    return new ApplicationListener<>() {
      @Override
      public void onApplicationEvent(PayloadApplicationEvent<?> event) {
        if (event.getPayload() instanceof PluginStatusesChangedEvent) {
          throw new IllegalStateException("status listener failed");
        }
      }
    };
  }

}
