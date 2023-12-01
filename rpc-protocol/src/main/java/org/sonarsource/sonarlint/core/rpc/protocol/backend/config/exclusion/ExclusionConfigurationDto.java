package org.sonarsource.sonarlint.core.rpc.protocol.backend.config.exclusion;

import java.nio.file.Path;
import java.util.List;
import org.sonarsource.sonarlint.core.rpc.protocol.common.ClientFileDto;

public class ExclusionConfigurationDto {

  private final List<Path> fileExclusions;
  private final List<Path> directoryExclusions;
  private final List<String> globPatternExclusions;

  /**
   *
   * @param fileExclusions Should match {@link ClientFileDto#getRelativePath()}
   * @param directoryExclusions Should match any parent dir of {@link ClientFileDto#getRelativePath()}
   * @param globPatternExclusions Patterns using the Java glob syntax.
   */
  public ExclusionConfigurationDto(List<Path> fileExclusions, List<Path> directoryExclusions, List<String> globPatternExclusions) {
    this.fileExclusions = fileExclusions;
    this.directoryExclusions = directoryExclusions;
    this.globPatternExclusions = globPatternExclusions;
  }

  public List<Path> getFileExclusions() {
    return fileExclusions;
  }

  public List<Path> getDirectoryExclusions() {
    return directoryExclusions;
  }

  public List<String> getGlobPatternExclusions() {
    return globPatternExclusions;
  }
}
