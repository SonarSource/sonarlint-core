package org.sonarsource.sonarlint.core.analysis.container.module;

import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import org.sonarsource.sonarlint.core.analysis.api.ClientFileSystem.FileType;
import org.sonarsource.sonarlint.core.analysis.api.ClientInputFile;

class TranscientModuleFileSystem implements ModuleFileSystem {

  private final Iterable<ClientInputFile> filesToAnalyze;

  TranscientModuleFileSystem(Iterable<ClientInputFile> filesToAnalyze) {
    this.filesToAnalyze = filesToAnalyze;
  }

  @Override
  public Stream<ClientInputFile> listFiles(String suffix, FileType type) {
    return listAllFiles()
      .filter(file -> file.relativePath().endsWith(suffix))
      .filter(file -> file.isTest() == (type == FileType.TEST));
  }

  @Override
  public Stream<ClientInputFile> listAllFiles() {
    return StreamSupport.stream(filesToAnalyze.spliterator(), false);
  }
}
