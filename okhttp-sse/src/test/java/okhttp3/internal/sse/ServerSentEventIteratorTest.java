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
import okio.Buffer;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public final class ServerSentEventIteratorTest {
  @Test public void multiline() throws IOException {
    Buffer source = new Buffer().writeUtf8(""
        + "data: YHOO\n"
        + "data: +2\n"
        + "data: 10\n"
        + "\n");
    ServerSentEventIterator iterator = new ServerSentEventIterator(source);
    assertEquals(new Event(null, null, "YHOO\n+2\n10"), iterator.next());
    assertNull(iterator.next());
  }

  @Test public void multilineCr() throws IOException {
    Buffer source = new Buffer().writeUtf8(""
        + "data: YHOO\r"
        + "data: +2\r"
        + "data: 10\r"
        + "\r");
    ServerSentEventIterator iterator = new ServerSentEventIterator(source);
    assertEquals(new Event(null, null, "YHOO\n+2\n10"), iterator.next());
    assertNull(iterator.next());
  }

  @Test public void multilineCrLf() throws IOException {
    Buffer source = new Buffer().writeUtf8(""
        + "data: YHOO\r\n"
        + "data: +2\r\n"
        + "data: 10\r\n"
        + "\r\n");
    ServerSentEventIterator iterator = new ServerSentEventIterator(source);
    assertEquals(new Event(null, null, "YHOO\n+2\n10"), iterator.next());
    assertNull(iterator.next());
  }

  @Test public void eventType() throws IOException {
    Buffer source = new Buffer().writeUtf8(""
        + "event: add\n"
        + "data: 73857293\n"
        + "\n"
        + "event: remove\n"
        + "data: 2153\n"
        + "\n"
        + "event: add\n"
        + "data: 113411\n"
        + "\n");
    ServerSentEventIterator iterator = new ServerSentEventIterator(source);
    assertEquals(new Event(null, "add", "73857293"), iterator.next());
    assertEquals(new Event(null, "remove", "2153"), iterator.next());
    assertEquals(new Event(null, "add", "113411"), iterator.next());
    assertNull(iterator.next());
  }

  @Test public void commentsIgnored() throws IOException {
    Buffer source = new Buffer().writeUtf8(""
        + ": test stream\n"
        + "\n"
        + "data: first event\n"
        + "id: 1\n"
        + "\n");
    ServerSentEventIterator iterator = new ServerSentEventIterator(source);
    assertEquals(new Event("1", null, "first event"), iterator.next());
    assertNull(iterator.next());
  }

  @Test public void idCleared() throws IOException {
    Buffer source = new Buffer().writeUtf8(""
        + "data: first event\n"
        + "id: 1\n"
        + "\n"
        + "data: second event\n"
        + "id\n"
        + "\n"
        + "data: third event\n"
        + "\n");
    ServerSentEventIterator iterator = new ServerSentEventIterator(source);
    assertEquals(new Event("1", null, "first event"), iterator.next());
    assertEquals(new Event(null, null, "second event"), iterator.next());
    assertEquals(new Event(null, null, "third event"), iterator.next());
    assertNull(iterator.next());
  }

  @Test public void nakedFieldNames() throws IOException {
    Buffer source = new Buffer().writeUtf8(""
        + "data\n"
        + "\n"
        + "data\n"
        + "data\n"
        + "\n"
        + "data:\n");
    ServerSentEventIterator iterator = new ServerSentEventIterator(source);
    assertEquals(new Event(null, null, ""), iterator.next());
    assertEquals(new Event(null, null, "\n"), iterator.next());
    assertNull(iterator.next());
  }

  @Test public void colonSpaceOptional() throws IOException {
    Buffer source = new Buffer().writeUtf8(""
        + "data:test\n"
        + "\n"
        + "data: test\n"
        + "\n");
    ServerSentEventIterator iterator = new ServerSentEventIterator(source);
    assertEquals(new Event(null, null, "test"), iterator.next());
    assertEquals(new Event(null, null, "test"), iterator.next());
    assertNull(iterator.next());
  }

  @Test public void leadingWhitespace() throws IOException {
    Buffer source = new Buffer().writeUtf8(""
        + "data:  test\n"
        + "\n");
    ServerSentEventIterator iterator = new ServerSentEventIterator(source);
    assertEquals(new Event(null, null, " test"), iterator.next());
    assertNull(iterator.next());
  }

  @Test public void idReusedAcrossEvents() throws IOException {
    Buffer source = new Buffer().writeUtf8(""
        + "data: first event\n"
        + "id: 1\n"
        + "\n"
        + "data: second event\n"
        + "\n"
        + "id: 2\n"
        + "data: third event\n"
        + "\n");
    ServerSentEventIterator iterator = new ServerSentEventIterator(source);
    assertEquals(new Event("1", null, "first event"), iterator.next());
    assertEquals(new Event("1", null, "second event"), iterator.next());
    assertEquals(new Event("2", null, "third event"), iterator.next());
    assertNull(iterator.next());
  }

  @Test public void idIgnoredFromEmptyEvent() throws IOException {
    Buffer source = new Buffer().writeUtf8(""
        + "data: first event\n"
        + "id: 1\n"
        + "\n"
        + "id: 2\n"
        + "\n"
        + "data: second event\n"
        + "\n");
    ServerSentEventIterator iterator = new ServerSentEventIterator(source);
    assertEquals(new Event("1", null, "first event"), iterator.next());
    assertEquals(new Event("1", null, "second event"), iterator.next());
    assertNull(iterator.next());
  }

  @Test public void retryDefault() throws IOException {
    Buffer source = new Buffer().writeUtf8(""
        + "data: first event\n"
        + "id: 1\n"
        + "\n");
    ServerSentEventIterator iterator = new ServerSentEventIterator(source);
    assertEquals(new Event("1", null, "first event"), iterator.next());
    assertNull(iterator.next());
    assertEquals(-1, iterator.reconnectionTime());
  }

  @Test public void retryParsed() throws IOException {
    Buffer source = new Buffer().writeUtf8(""
        + "retry: 22\n"
        + "\n"
        + "data: first event\n"
        + "id: 1\n"
        + "\n");
    ServerSentEventIterator iterator = new ServerSentEventIterator(source);
    assertEquals(new Event("1", null, "first event"), iterator.next());
    assertNull(iterator.next());
    assertEquals(22, iterator.reconnectionTime());
  }

  @Test public void retryInvalidFormatIgnored() throws IOException {
    Buffer source = new Buffer().writeUtf8(""
        + "retry: 22\n"
        + "\n"
        + "retry: hey"
        + "\n");
    ServerSentEventIterator iterator = new ServerSentEventIterator(source);
    assertNull(iterator.next());
    assertEquals(22, iterator.reconnectionTime());
  }
}
