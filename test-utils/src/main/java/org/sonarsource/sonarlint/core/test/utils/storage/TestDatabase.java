/*
 * SonarLint Core - Test Utils
 * Copyright (C) 2016-2025 SonarSource Sàrl
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
package org.sonarsource.sonarlint.core.test.utils.storage;

import java.nio.file.Path;
import org.flywaydb.core.Flyway;
import org.h2.jdbcx.JdbcConnectionPool;
import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.sonarsource.sonarlint.core.commons.storage.SonarLintDatabase;
import org.sonarsource.sonarlint.core.serverconnection.FileUtils;

/**
 * Having a separate database entrypoint than the production one allows for more flexibility for tests (e.g. setup DB in an unexpected state, or test migrations).
 */
public class TestDatabase {
  private final JdbcConnectionPool dataSource;
  private final DSLContext dsl;

  public TestDatabase(Path storageRoot) {
    var baseDir = storageRoot.resolve("h2");
    FileUtils.mkdirs(baseDir);
    var dbBasePath = baseDir.resolve(SonarLintDatabase.SQ_IDE_DB_FILENAME).toAbsolutePath();
    this.dataSource = JdbcConnectionPool.create("jdbc:h2:" + dbBasePath, "sa", "");

    var flyway = Flyway.configure()
      .dataSource(this.dataSource)
      .locations("classpath:db/migration")
      .defaultSchema("PUBLIC")
      .schemas("PUBLIC")
      .createSchemas(true)
      .baselineOnMigrate(true)
      .failOnMissingLocations(false)
      .load();
    // this might throw but it's fine. If migrations fail, we want to fail starting the backend
    flyway.migrate();

    System.setProperty("org.jooq.no-tips", "true");
    System.setProperty("org.jooq.no-logo", "true");
    this.dsl = DSL.using(this.dataSource, SQLDialect.H2);
  }

  public DSLContext dsl() {
    return dsl;
  }

  public void shutdown() {
    dataSource.dispose();
  }

}
