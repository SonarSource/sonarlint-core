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
import fi.iki.elonen.NanoHTTPD;
import fi.iki.elonen.NanoHTTPD.Response.Status;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.Socket;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Base64;
import java.util.Locale;
import java.util.Objects;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.eclipse.lsp4j.jsonrpc.Launcher;
import org.eclipse.lsp4j.launch.LSPLauncher;
import org.eclipse.lsp4j.services.LanguageClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sonarsource.sonarlint.core.client.api.common.RuleDetails;

public class ServerMain {
  public static final ObjectMapper JSON = new ObjectMapper().registerModule(new Jdk8Module())
    .registerModule(new JSR310Module())
    .registerModule(pathAsJson())
    .configure(JsonGenerator.Feature.AUTO_CLOSE_TARGET, false);

  private static final Logger LOG = LoggerFactory.getLogger(ServerMain.class);

  private ServerMain() {
  }

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

    LOG.info("Connecting to {}", port);

    Socket socket = new Socket("localhost", Integer.parseInt(port));

    InputStream in = socket.getInputStream();
    OutputStream out = socket.getOutputStream();

    OutputStream intercept = new OutputStream() {

      @Override
      public void write(int b) throws IOException {
        out.write(b);
      }
    };

    LOG.info("Connected to parent using socket on port {}", port);

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

    String ruleServerPort = System.getProperty("sonarlint.rulePort");
    Objects.requireNonNull(ruleServerPort, "-Dsonarlint.rulePort=? is required");
    try {
      new NanoHTTPD(Integer.parseInt(ruleServerPort)) {
        @Override
        public Response serve(IHTTPSession session) {
          String ruleKey = session.getParms().get("ruleKey");
          if (ruleKey == null) {
            return newFixedLengthResponse(Status.BAD_REQUEST, NanoHTTPD.MIME_PLAINTEXT, "Missing 'ruleKey' parameter");
          } else {
            RuleDetails ruleDetails = server.getRuleDescription(ruleKey);
            if (ruleDetails == null) {
              return newFixedLengthResponse(Status.NOT_FOUND, NanoHTTPD.MIME_PLAINTEXT, "No rule with key '" + ruleKey + "'");
            }
            try {
              String ruleName = ruleDetails.getName();
              String htmlDescription = ruleDetails.getHtmlDescription();
              String type = ruleDetails.getType().toLowerCase(Locale.ENGLISH);
              String typeImg64 = getAsBase64("/images/type/" + type + ".png");
              String severity = ruleDetails.getSeverity().toLowerCase(Locale.ENGLISH);
              String severityImg64 = getAsBase64("/images/severity/" + severity + ".png");
              return newFixedLengthResponse("<!doctype html><html><head>" + css() + "</head><body><h1><big>"
                + ruleName + "</big> (" + ruleKey
                + ")</h1>"
                + "<div>"
                + "<img style=\"padding-bottom: 1px;vertical-align: middle\" width=\"16\" height=\"16\" alt=\"" + type + "\" src=\"data:image/gif;base64," + typeImg64 + "\">&nbsp;"
                + clean(type)
                + "&nbsp;"
                + "<img style=\"padding-bottom: 1px;vertical-align: middle\" width=\"16\" height=\"16\" alt=\"" + severity + "\" src=\"data:image/gif;base64," + severityImg64
                + "\">&nbsp;"
                + clean(severity)
                + "</div>"
                + "<div class=\"rule-desc\">" + htmlDescription
                + "</div></body></html>");
            } catch (Exception e) {
              StringWriter sw = new StringWriter();
              PrintWriter pw = new PrintWriter(sw);
              e.printStackTrace(pw);
              return newFixedLengthResponse(Status.INTERNAL_ERROR, NanoHTTPD.MIME_PLAINTEXT, e.getMessage() + "\n" + sw.toString());
            }
          }
        }
      }.start(NanoHTTPD.SOCKET_READ_TIMEOUT, false);
    } catch (IOException e) {
      server.error("Unable to start rule server", e);
      throw new IllegalStateException(e);
    }
  }

  private static String getAsBase64(String image) throws IOException {
    InputStream resourceAsStream = ServerMain.class.getResourceAsStream(image);
    if (resourceAsStream == null) {
      throw new IllegalStateException("Unable to load image " + image);
    }
    return Base64.getEncoder().encodeToString(IOUtils.toByteArray(resourceAsStream));
  }

  private static String clean(String txt) {
    return StringUtils.capitalize(txt.toLowerCase(Locale.ENGLISH).replace("_", " "));
  }

  private static String css() {
    return "<style type=\"text/css\">"
      + "body { font-family: Helvetica Neue,Segoe UI,Helvetica,Arial,sans-serif; font-size: 13px; line-height: 1.23076923; "
      + "color: #444;"
      + "}"
      + "h1 { color: #444;font-size: 14px;font-weight: 500; }"
      + "h2 { line-height: 24px; color: #444;}"
      + "a { border-bottom: 1px solid #cae3f2; color: #236a97; cursor: pointer; outline: none; text-decoration: none; transition: all .2s ease;}"
      + ".rule-desc { line-height: 1.5;}"
      + ".rule-desc { line-height: 1.5;}"
      + ".rule-desc h2 { font-size: 16px; font-weight: 400;}"
      + ".rule-desc code { padding: .2em .45em; margin: 0; border-radius: 3px; white-space: nowrap;}"
      + ".rule-desc pre { padding: 10px; border-top: 1px solid #e6e6e6; border-bottom: 1px solid #e6e6e6; line-height: 18px; overflow: auto;}"
      + ".rule-desc code, .rule-desc pre { font-family: Consolas,Liberation Mono,Menlo,Courier,monospace; font-size: 12px;}"
      + ".rule-desc ul { padding-left: 40px; list-style: disc;}</style>";
  }
}
