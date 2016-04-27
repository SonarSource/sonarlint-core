/*
 * SonarLint Core - ITs
 * Copyright (C) 2009-2016 SonarSource SA
 * mailto:contact AT sonarsource DOT com
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
package its.tools;

import org.apache.commons.io.FileUtils;
import org.junit.rules.ExternalResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import static com.jayway.awaitility.Awaitility.*;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

public class SonarlintDaemon extends ExternalResource {
  private static final Logger LOG = LoggerFactory.getLogger(SonarlintDaemon.class);
  private static final String READY_LOG = "Server started";
  private static String artifactVersion;
  private Path script;

  private Path installPath;
  private CommandExecutor exec;

  public void install() {
    install(artifactVersion());
  }
  
  private static String artifactVersion() {
    if (artifactVersion == null) {
      try (FileInputStream fis = new FileInputStream(new File("../daemon/target/maven-archiver/pom.properties"))) {
        Properties props = new Properties();
        props.load(fis);
        artifactVersion = props.getProperty("version");
        return artifactVersion;
      } catch (IOException e) {
        throw new IllegalStateException(e);
      }
    }
    return artifactVersion;
  }

  public void install(String version) {
    SonarlintInstaller installer = new SonarlintInstaller();
    this.script = installer.install(installPath, version);
  }

  @Override
  protected void before() throws Throwable {
    installPath = Files.createTempDirectory("sonarlint-daemon");
  }

  @Override
  protected void after() {
    if (exec != null) {
      exec.destroy();
      exec = null;
    }
    if (installPath != null) {
      FileUtils.deleteQuietly(installPath.toFile());
      installPath = null;
    }
  }

  public void waitReady() {
    await().atMost(10, TimeUnit.SECONDS).until(new Callable<Boolean>() {
      @Override
      public Boolean call() throws Exception {
        return getOut().contains(READY_LOG);
      }
    });
  }

  public void run(String... args) {
    LOG.info("Running SonarLint Daemon");
    try {
      exec = new CommandExecutor(script);
      exec.execute(args);
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
  }

  public String getOut() {
    return exec.getStdOut();
  }

  public String[] getOutLines() {
    return getOut().split(System.lineSeparator());
  }

  public Path getSonarlintInstallation() {
    return script.getParent().getParent();
  }

  public String getErr() {
    return exec.getStdErr();
  }

  public String[] getErrLines() {
    return getErr().split(System.lineSeparator());
  }
}
