/*
 * Copyright (C) 2018 Square, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package okhttp3.internal.sse;

import java.io.IOException;
import javax.annotation.Nullable;
import okio.BufferedSource;
import okio.ByteString;

/**
 * Parses <a href="https://html.spec.whatwg.org/multipage/server-sent-events.html">server-sent
 * events</a> from a {@link BufferedSource}.
 */
public final class ServerSentEventIterator {
  private static final ByteString CRLF = ByteString.encodeUtf8("\r\n");

  private final BufferedSource source;
  private String lastId = null;
  private long reconnectionTimeMs = -1;

  /**
   * @param source The source from which events are read. A BOM, if present, must have already been
   * skipped. It is the caller's responsibility to close this source.
   */
  public ServerSentEventIterator(BufferedSource source) {
    this.source = source;
  }

  /** The last parsed reconnection time (in milliseconds), or -1 if unspecified. */
  public long reconnectionTime() {
    return reconnectionTimeMs;
  }

  /** Reads a single event from the stream, or null when exhausted. */
  public @Nullable Event next() throws IOException {
    String id = lastId;
    String name = null;
    StringBuilder data = null;

    while (true) {
      long crlf = source.indexOfElement(CRLF);
      if (crlf == -1L) {
        return null;
      }

      if (crlf == 0L) {
        skipCrAndOrLf();

        if (data != null) {
          lastId = id;
          return new Event(id, name, data.toString());
        }

        // Event with no data. Reject and try again.
        id = lastId;
        name = null;
        continue;
      }

      long colon = source.indexOf((byte) ':', 0, crlf);
      if (colon == 0L) {
        // Comment line. Skip in its entirety.
        source.skip(crlf);
        skipCrAndOrLf();
        continue;
      }

      String fieldName;
      String fieldValue;
      if (colon == -1L) {
        fieldName = source.readUtf8(crlf);
        fieldValue = "";
      } else {
        fieldName = source.readUtf8(colon);
        crlf -= colon;

        source.skip(1L);
        crlf--;

        // No need to request(1) before checking for the optional space because we've buffered
        // enough to see the line ending which is at worst the next byte.
        if (source.buffer().getByte(0) == ' ') {
          source.skip(1L);
          crlf--;
        }

        fieldValue = source.readUtf8(crlf);
      }
      skipCrAndOrLf();

      switch (fieldName) {
        case "data":
          if (data == null) {
            data = new StringBuilder();
          } else {
            data.append('\n');
          }
          data.append(fieldValue);
          break;

        case "id":
          if (fieldValue.isEmpty()) {
            fieldValue = null;
          }
          id = fieldValue;
          break;

        case "event":
          if (fieldValue.isEmpty()) {
            fieldValue = null;
          }
          name = fieldValue;
          break;

        case "retry":
          try {
            reconnectionTimeMs = Long.parseLong(fieldValue);
          } catch (NumberFormatException ignored) {
          }
          break;

        default:
          source.skip(crlf);
          skipCrAndOrLf();
          break;
      }
    }
  }

  /** Consumes {@code \r}, {@code \r\n}, or {@code \n} from {@link #source}. */
  private void skipCrAndOrLf() throws IOException {
    if ((source.readByte() & 0xff) == '\r'
        && source.request(1)
        && source.buffer().getByte(0) == '\n') {
      source.skip(1);
    }
  }
}
