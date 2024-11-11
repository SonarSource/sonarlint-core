package org.sonarsource.sonarlint.core.storage;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Date;
import org.apache.commons.io.FileUtils;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;

public class XodusPurgeUtils {

  private XodusPurgeUtils() {
    // Static class
  }

  private static final SonarLintLogger LOG = SonarLintLogger.get();

  public static void purgeOldTemporaryFiles(Path workDir, Integer purgeDays, String pattern) {
    if (Files.exists(workDir)) {
      try (var stream = Files.newDirectoryStream(workDir, pattern)) {
        for (var path : stream) {
          var file = path.toFile();
          var diff = new Date().getTime() - file.lastModified();
          if (diff > purgeDays * 24 * 60 * 60 * 1000) {
            FileUtils.deleteQuietly(file);
            LOG.debug("Successfully purged " + path);
          }
        }
      } catch (Exception e) {
        LOG.error("Unable to purge old temporary files for pattern " + pattern);
      }
    }
  }

}
