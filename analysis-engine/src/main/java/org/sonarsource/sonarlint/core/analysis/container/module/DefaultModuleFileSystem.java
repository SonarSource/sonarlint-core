package org.sonarsource.sonarlint.core.analysis.container.module;

import java.util.stream.Stream;
import org.sonarsource.sonarlint.core.analysis.api.ClientFileSystem;
import org.sonarsource.sonarlint.core.analysis.api.ClientFileSystem.FileType;
import org.sonarsource.sonarlint.core.analysis.api.ClientInputFile;

/**
 * This implementation will query the client
 */
class DefaultModuleFileSystem implements ModuleFileSystem {

  private final String moduleId;
  private final ClientFileSystem clientFs;

  public DefaultModuleFileSystem(String moduleId, ClientFileSystem clientFs) {
    this.moduleId = moduleId;
    this.clientFs = clientFs;
  }

  @Override
  public Stream<ClientInputFile> listFiles(String suffix, FileType type) {
    return clientFs.listFiles(moduleId, suffix, type);
  }

  @Override
  public Stream<ClientInputFile> listAllFiles() {
    return clientFs.listAllFiles(moduleId);
  }

}
