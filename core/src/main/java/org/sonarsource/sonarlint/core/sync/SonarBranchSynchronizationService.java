package org.sonarsource.sonarlint.core.sync;

import javax.inject.Named;
import javax.inject.Singleton;
import org.sonarsource.sonarlint.core.ServerApiProvider;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;
import org.sonarsource.sonarlint.core.serverapi.ServerApi;
import org.sonarsource.sonarlint.core.serverapi.branches.ServerBranch;
import org.sonarsource.sonarlint.core.serverconnection.ProjectBranches;
import org.sonarsource.sonarlint.core.storage.StorageService;

import static java.util.stream.Collectors.toSet;

@Named
@Singleton
public class SonarBranchSynchronizationService {

  private static final SonarLintLogger LOG = SonarLintLogger.get();

  private final StorageService storageService;
  private final ServerApiProvider serverApiProvider;

  public SonarBranchSynchronizationService(StorageService storageService, ServerApiProvider serverApiProvider) {
    this.storageService = storageService;
    this.serverApiProvider = serverApiProvider;
  }

  public void sync(String connectionId, String sonarProjectKey) {
    serverApiProvider.getServerApi(connectionId).ifPresent(serverApi -> {
      var projectBranches = synchronizeProjectBranches(serverApi, sonarProjectKey);
      storageService.getStorageFacade().connection(connectionId).project(sonarProjectKey).branches().store(projectBranches);
    });
  }

  private static ProjectBranches synchronizeProjectBranches(ServerApi serverApi, String projectKey) {
    LOG.info("Synchronizing project branches for project '{}'", projectKey);
    var allBranches = serverApi.branches().getAllBranches(projectKey);
    var mainBranch = allBranches.stream().filter(ServerBranch::isMain).findFirst().map(ServerBranch::getName)
      .orElseThrow(() -> new IllegalStateException("No main branch for project '" + projectKey + "'"));
    return new ProjectBranches(allBranches.stream().map(ServerBranch::getName).collect(toSet()), mainBranch);
  }


}
