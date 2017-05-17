/*
 * SonarLint Language Server
 * Copyright (C) 2009-2017 SonarSource SA
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
package org.sonarlint.languageserver;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JSR310Module;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Objects;
import org.eclipse.lsp4j.jsonrpc.Launcher;
import org.eclipse.lsp4j.launch.LSPLauncher;
import org.eclipse.lsp4j.services.LanguageClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ServerMain {
  public static final ObjectMapper JSON = new ObjectMapper().registerModule(new Jdk8Module())
    .registerModule(new JSR310Module())
    .registerModule(pathAsJson())
    .configure(JsonGenerator.Feature.AUTO_CLOSE_TARGET, false);

  private static final Logger LOG = LoggerFactory.getLogger(ServerMain.class);

  private static SimpleModule pathAsJson() {
    SimpleModule m = new SimpleModule();

    m.addSerializer(Path.class, new JsonSerializer<Path>() {
      @Override
      public void serialize(Path path,
        JsonGenerator gen,
        SerializerProvider serializerProvider) throws IOException, JsonProcessingException {
        gen.writeString(path.toString());
      }
    });

    m.addDeserializer(Path.class, new JsonDeserializer<Path>() {
      @Override
      public Path deserialize(JsonParser parse,
        DeserializationContext deserializationContext) throws IOException, JsonProcessingException {
        return Paths.get(parse.getText());
      }
    });

    return m;
  }

  public static void main(String[] args) throws IOException {
    try {
      Connection connection = connectToNode();

      run(connection);
    } catch (Throwable t) {
      LOG.error(t.getMessage(), t);

      System.exit(1);
    }
  }

  private static Connection connectToNode() throws IOException {
    String port = System.getProperty("sonarlint.port");

    Objects.requireNonNull(port, "-Dsonarlint.port=? is required");

    LOG.info("Connecting to " + port);

    Socket socket = new Socket("localhost", Integer.parseInt(port));

    InputStream in = socket.getInputStream();
    OutputStream out = socket.getOutputStream();

    OutputStream intercept = new OutputStream() {

      @Override
      public void write(int b) throws IOException {
        out.write(b);
      }
    };

    LOG.info("Connected to parent using socket on port " + port);

    return new Connection(in, intercept);
  }

  private static class Connection {
    final InputStream in;
    final OutputStream out;

    private Connection(InputStream in, OutputStream out) {
      this.in = in;
      this.out = out;
    }
  }

  /**
  * Listen for requests from the parent node process.
  * Send replies asynchronously.
  * When the request stream is closed, wait for 5s for all outstanding responses to compute, then return.
  */
  public static void run(Connection connection) {
    SonarLintLanguageServer server = new SonarLintLanguageServer();
    Launcher<LanguageClient> launcher = LSPLauncher.createServerLauncher(server, connection.in, connection.out, true, new PrintWriter(System.out));

    server.connect(launcher.getRemoteProxy());
    launcher.startListening();
  }
}
