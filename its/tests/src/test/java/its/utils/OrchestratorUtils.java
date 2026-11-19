/*
 * SonarLint Core - ITs - Tests
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
package its.utils;

import com.sonar.orchestrator.config.Configuration;
import com.sonar.orchestrator.junit5.OrchestratorExtension;
import com.sonar.orchestrator.junit5.OrchestratorExtensionBuilder;
import javax.annotation.CheckForNull;

import static its.utils.ItUtils.SONAR_VERSION;

public class OrchestratorUtils {
  /**
   * Set by Surefire to the number of the fork running the tests, see its/tests/pom.xml.
   */
  static final String FORK_NUMBER_PROPERTY = "its.forkNumber";
  /**
   * Below the Linux ephemeral port range (32768-60999), so the OS never hands out these ports to another process.
   */
  static final int FORK_PORT_BASE = 20_000;
  static final int PORTS_PER_FORK = 10;

  public static OrchestratorExtensionBuilder defaultEnvBuilder() {
    var config = Configuration.builder().addEnvVariables().addSystemProperties();
    var orchestratorJavaHome = System.getenv().get("ORCHESTRATOR_JAVA_HOME");
    if (orchestratorJavaHome != null) {
      config.setProperty("java.home", orchestratorJavaHome);
    }
    var forkPortBase = forkPortBase();
    if (forkPortBase != null) {
      // Orchestrator reads the H2 URL from its configuration and starts the embedded database on the URL's port
      config.setProperty("sonar.jdbc.url", "jdbc:h2:tcp://127.0.0.1:" + (forkPortBase + 3) + "/sonar;USER=sonar;PASSWORD=sonar");
    }
    var builder = OrchestratorExtension.builder(config.build())
      .defaultForceAuthentication()
      .keepBundledPlugins()
      .useDefaultAdminCredentialsForBuilds(true)
      .setSonarVersion(SONAR_VERSION);
    if (forkPortBase != null) {
      builder
        .setServerProperty("sonar.web.port", String.valueOf(forkPortBase))
        .setServerProperty("sonar.search.port", String.valueOf(forkPortBase + 1))
        .setServerProperty("sonar.es.port", String.valueOf(forkPortBase + 2));
    }
    return builder;
  }

  /**
   * Orchestrator picks a free port, releases it and only binds it when the server starts, so servers started at the same time
   * in parallel forks can get the same port. Each fork gets its own fixed web, Elasticsearch and H2 ports instead.
   */
  @CheckForNull
  private static Integer forkPortBase() {
    var forkNumber = Integer.getInteger(FORK_NUMBER_PROPERTY);
    return forkNumber == null ? null : (FORK_PORT_BASE + forkNumber * PORTS_PER_FORK);
  }
}
