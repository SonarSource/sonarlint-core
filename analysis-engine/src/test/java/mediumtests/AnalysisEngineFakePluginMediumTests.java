/*
 * SonarLint Core - Analysis Engine
 * Copyright (C) 2016-2023 SonarSource SA
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
package mediumtests;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;
import org.sonar.api.Plugin;
import org.sonar.api.Startable;
import org.sonar.api.batch.sensor.Sensor;
import org.sonar.api.batch.sensor.SensorContext;
import org.sonar.api.batch.sensor.SensorDescriptor;
import org.sonarsource.api.sonarlint.SonarLintSide;
import org.sonarsource.sonarlint.core.analysis.AnalysisEngine;
import org.sonarsource.sonarlint.core.analysis.api.AnalysisConfiguration;
import org.sonarsource.sonarlint.core.analysis.api.AnalysisEngineConfiguration;
import org.sonarsource.sonarlint.core.analysis.api.ClientModuleFileEvent;
import org.sonarsource.sonarlint.core.analysis.api.ClientModuleInfo;
import org.sonarsource.sonarlint.core.commons.Language;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogTester;
import org.sonarsource.sonarlint.core.commons.progress.CanceledException;
import org.sonarsource.sonarlint.core.commons.progress.ProgressMonitor;
import org.sonarsource.sonarlint.core.plugin.commons.loading.LoadedPlugins;
import org.sonarsource.sonarlint.plugin.api.module.file.ModuleFileEvent;
import org.sonarsource.sonarlint.plugin.api.module.file.ModuleFileListener;
import testutils.OnDiskTestClientInputFile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static testutils.ClientFileSystemFixtures.anEmptyClientFileSystem;

class AnalysisEngineFakePluginMediumTests {
  @RegisterExtension
  SonarLintLogTester logTester = new SonarLintLogTester();

  private AnalysisEngine analysisEngine;
  private AccumulatingModuleFileListener recordingModuleFileListener = new AccumulatingModuleFileListener();
  private BlockingModuleFileListener blockingModuleFileListener = new BlockingModuleFileListener();
  private BlockingSensor blockingSensor = new BlockingSensor();

  @BeforeEach
  void prepare(@TempDir Path workDir) throws IOException {
    var enabledLanguages = Set.of(Language.PYTHON);
    var analysisGlobalConfig = AnalysisEngineConfiguration.builder()
      .setClientPid(1234L)
      .setWorkDir(workDir)
      .build();
    this.analysisEngine = new AnalysisEngine(analysisGlobalConfig, new LoadedPlugins(Map.of("python", new FakeSonarPlugin()), List.of(), List.of()), logTester.getLogOutput());
  }

  private class FakeSonarPlugin implements Plugin {

    @Override
    public void define(Context context) {
      context.addExtension(StartableModuleLevelComponent.class);
      context.addExtensions(recordingModuleFileListener, blockingModuleFileListener, blockingSensor);
    }
  }

  @SonarLintSide(lifespan = "MODULE")
  private static class StartableModuleLevelComponent implements Startable {

    private static final AtomicBoolean started = new AtomicBoolean();
    private static final AtomicBoolean stopped = new AtomicBoolean();

    @Override
    public void start() {
      started.set(true);
    }

    @Override
    public void stop() {
      // Emulate a long stop
      try {
        Thread.sleep(2000);
      } catch (InterruptedException e) {
        throw new RuntimeException(e);
      }
      stopped.set(true);
    }
  }

  @AfterEach
  void cleanUp() {
    this.analysisEngine.stop();
  }

  @Test
  void should_forward_module_file_event_to_listener() {
    var clientInputFile = new OnDiskTestClientInputFile(Paths.get("main.py"), "main.py", false, StandardCharsets.UTF_8, null);
    analysisEngine.registerModule(new ClientModuleInfo("moduleKey", anEmptyClientFileSystem()));

    analysisEngine.fireModuleFileEvent("moduleKey", ClientModuleFileEvent.of(clientInputFile, ModuleFileEvent.Type.CREATED));

    await().untilAsserted(() -> assertThat(recordingModuleFileListener.events).hasSize(1));
  }

  @SonarLintSide(lifespan = "MODULE")
  static class AccumulatingModuleFileListener implements ModuleFileListener {
    private final List<ModuleFileEvent> events = new ArrayList<>();

    @Override
    public void process(ModuleFileEvent event) {
      events.add(event);
    }
  }

  @SonarLintSide(lifespan = "MODULE")
  static class BlockingModuleFileListener implements ModuleFileListener {

    private boolean beforeLock;
    private boolean afterLock;
    private final ReentrantLock lock = new ReentrantLock();

    @Override
    public void process(ModuleFileEvent event) {
      this.beforeLock = true;
      lock.lock();
      this.afterLock = true;
    }
  }

  static class BlockingSensor implements Sensor {

    private boolean beforeLock;
    private boolean afterLock;
    private boolean cancelled;
    private final ReentrantLock lock = new ReentrantLock();

    AtomicInteger executionCount = new AtomicInteger();

    @Override
    public void describe(SensorDescriptor descriptor) {
    }

    @Override
    public void execute(SensorContext context) {
      this.beforeLock = true;
      lock.lock();
      this.cancelled = context.isCancelled();
      executionCount.incrementAndGet();
      lock.unlock();
      this.afterLock = true;
    }
  }

  @Test
  void should_execute_pending_commands_when_stopping() {
    var clientInputFile = new OnDiskTestClientInputFile(Paths.get("main.py"), "main.py", false, StandardCharsets.UTF_8, null);

    analysisEngine.registerModule(new ClientModuleInfo("moduleKey", anEmptyClientFileSystem()));

    // Make the event listener block
    blockingModuleFileListener.lock.lock();
    analysisEngine.fireModuleFileEvent("moduleKey", ClientModuleFileEvent.of(clientInputFile, ModuleFileEvent.Type.CREATED));
    await().untilAsserted(() -> assertThat(blockingModuleFileListener.beforeLock).isTrue());

    analysisEngine.stop();
    assertThat(blockingModuleFileListener.afterLock).isFalse();

    blockingModuleFileListener.lock.unlock();
    await().untilAsserted(() -> assertThat(blockingModuleFileListener.afterLock).isTrue());
  }

  @Test
  void should_execute_pending_commands_when_unloading_modules() {
    var clientInputFile = new OnDiskTestClientInputFile(Paths.get("main.py"), "main.py", false, StandardCharsets.UTF_8, null);

    analysisEngine.registerModule(new ClientModuleInfo("moduleKey", anEmptyClientFileSystem()));

    // Make the event listener block
    blockingModuleFileListener.lock.lock();
    analysisEngine.fireModuleFileEvent("moduleKey", ClientModuleFileEvent.of(clientInputFile, ModuleFileEvent.Type.CREATED));
    await().untilAsserted(() -> assertThat(blockingModuleFileListener.beforeLock).isTrue());

    analysisEngine.unregisterModule("moduleKey");
    assertThat(blockingModuleFileListener.afterLock).isFalse();

    blockingModuleFileListener.lock.unlock();
    await().untilAsserted(() -> assertThat(blockingModuleFileListener.afterLock).isTrue());
  }

  @Test
  void should_cancel_all_analysis_from_all_modules_when_stopping() throws InterruptedException {
    analysisEngine.registerModule(new ClientModuleInfo("moduleKey1", anEmptyClientFileSystem()));
    analysisEngine.registerModule(new ClientModuleInfo("moduleKey2", anEmptyClientFileSystem()));

    var thrownException1 = new AtomicReference<Exception>();
    var thrownException2 = new AtomicReference<Exception>();

    blockingSensor.lock.lock();
    var analysisThread1 = new Thread(() -> {
      try {
        analysisEngine.analyze("moduleKey1", AnalysisConfiguration.builder().build(), i -> {
        }, null, new ProgressMonitor(null));
      } catch (Exception e) {
        thrownException1.set(e);
      }
    });
    analysisThread1.start();
    var analysisThread2 = new Thread(() -> {
      try {
        analysisEngine.analyze("moduleKey2", AnalysisConfiguration.builder().build(), i -> {
        }, null, new ProgressMonitor(null));
      } catch (Exception e) {
        thrownException2.set(e);
      }
    });
    analysisThread2.start();

    // Analysis 1 is executing, while Analysis 2 is in the queue
    await().untilAsserted(() -> assertThat(blockingSensor.beforeLock).isTrue());

    analysisEngine.stop();
    assertThat(blockingSensor.afterLock).isFalse();

    blockingSensor.lock.unlock();
    await().untilAsserted(() -> assertThat(blockingSensor.afterLock).isTrue());

    assertThat(blockingSensor.cancelled).isTrue();

    analysisThread1.join();
    analysisThread2.join();

    assertThat(thrownException1).hasValueMatching(e -> e instanceof CanceledException);
    assertThat(thrownException2).hasValueMatching(e -> e instanceof CanceledException);
    assertThat(blockingSensor.executionCount.get()).isEqualTo(1);
  }

  @Test
  void should_only_cancel_analysis_from_the_same_module_when_unloading() throws InterruptedException {
    analysisEngine.registerModule(new ClientModuleInfo("moduleKey1", anEmptyClientFileSystem()));
    analysisEngine.registerModule(new ClientModuleInfo("moduleKey2", anEmptyClientFileSystem()));

    var thrownException11 = new AtomicReference<Exception>();
    var thrownException12 = new AtomicReference<Exception>();
    var thrownException2 = new AtomicReference<Exception>();

    blockingSensor.lock.lock();
    var analysisThread11 = new Thread(() -> {
      try {
        analysisEngine.analyze("moduleKey1", AnalysisConfiguration.builder().build(), i -> {
        }, null, new ProgressMonitor(null));
      } catch (Exception e) {
        thrownException11.set(e);
      }
    }, "Analysis 1-1");
    analysisThread11.start();

    // Wait for analysis 11 to be executing, before putting Analysis 12 and Analysis 2 in the queue
    await().untilAsserted(() -> assertThat(blockingSensor.beforeLock).isTrue());

    var analysisThread12 = new Thread(() -> {
      try {
        analysisEngine.analyze("moduleKey1", AnalysisConfiguration.builder().build(), i -> {
        }, null, new ProgressMonitor(null));
      } catch (Exception e) {
        thrownException12.set(e);
      }
    }, "Analysis 1-2");
    analysisThread12.start();
    var analysisThread2 = new Thread(() -> {
      try {
        analysisEngine.analyze("moduleKey2", AnalysisConfiguration.builder().build(), i -> {
        }, null, new ProgressMonitor(null));
      } catch (Exception e) {
        thrownException2.set(e);
      }
    }, "Analysis 2");
    analysisThread2.start();


    analysisEngine.unregisterModule("moduleKey1");
    assertThat(blockingSensor.afterLock).isFalse();

    blockingSensor.lock.unlock();
    await().untilAsserted(() -> assertThat(blockingSensor.afterLock).isTrue());


    analysisThread11.join();
    analysisThread12.join();
    analysisThread2.join();

    assertThat(thrownException11).hasValueMatching(e -> e instanceof CanceledException);
    assertThat(thrownException12).hasValueMatching(e -> e instanceof CanceledException);
    // Analysis 2 has not been cancelled, so the Sensor counter is 2
    assertThat(thrownException2).hasValue(null);
    assertThat(blockingSensor.executionCount.get()).isEqualTo(2);
  }

}
