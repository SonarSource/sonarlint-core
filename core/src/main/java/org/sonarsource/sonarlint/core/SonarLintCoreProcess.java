package org.sonarsource.sonarlint.core;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import org.eclipse.lsp4j.jsonrpc.Launcher;
import org.sonarsource.sonarlint.core.clientapi.SonarLintClient;
import picocli.CommandLine;

public class SonarLintCoreProcess implements Callable<Integer> {

  @Override
  public Integer call() throws ExecutionException, InterruptedException {
    var server = new SonarLintBackendImpl();

    var launcher = new Launcher.Builder<SonarLintClient>()
      .setLocalService(server)
      .setRemoteInterface(SonarLintClient.class)
      .setInput(System.in)
      .setOutput(System.out)
      .create();

    server.setClient(launcher.getRemoteProxy());
    launcher.startListening().get();
    return 0;
  }

  public static void main(String... args) {
    var exitCode = new CommandLine(new SonarLintCoreProcess()).execute(args);
    System.exit(exitCode);
  }
}
