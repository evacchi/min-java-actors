/*
 *    Copyright 2021 Edoardo Vacchi
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 *
 */

//JAVA 17
//JAVAC_OPTIONS --enable-preview --release 17
//JAVA_OPTIONS  --enable-preview
//REPOS jitpack=https://jitpack.io/
//DEPS com.github.evacchi:min-java-actors:main-SNAPSHOT

package io.github.evacchi.asyncchat;

import io.github.evacchi.Actor;

import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousSocketChannel;

import static io.github.evacchi.Actor.*;
import static java.lang.System.*;

public interface AsyncChannelActor {

    record Buffer(String content) {}
    record PoisonPill() {}

    record LineRead(String payload) {}
    record WriteLine(String payload) {}

    static final char END_LINE = '\n';

    static Actor.Behavior idle(Actor.Address self, Actor.Address parent, AsynchronousSocketChannel channel, String acc) {
        if (!channel.isOpen()) {
            err.println("channel closed");
            self.tell(new PoisonPill());
        } else {
            ByteBuffer buf = ByteBuffer.allocate(2048);
            channel.read(buf, channel,
                    Channels.handler(
                            (a,b) -> self.tell(new Buffer(new String(buf.array()))),
                            (exc,b) -> self.tell(new PoisonPill())));
        }
        return msg -> switch (msg) {
                case PoisonPill pp -> Die;
                case WriteLine line -> {
                    channel.write(ByteBuffer.wrap((line.payload() + END_LINE).getBytes()), channel,
                            Channels.handler(
                                    (ignored,ignored_) -> {},
                                    (exc,ignored) -> self.tell(new PoisonPill())));
                    yield Stay;
                }
                case Buffer buffer -> {
                    var line = acc + buffer.content;
                    var cr = line.indexOf(END_LINE);
                    if (cr >= 0) {
                        parent.tell(new LineRead(line.substring(0, cr)));
                        var rem = line.substring(cr + 2).trim();
                        yield Become(idle(self, parent, channel, rem));
                    } else {
                        yield Become(idle(self, parent, channel, ""));
                    }
                }
                default -> throw new RuntimeException("Unhandled message " + msg);
        };
    }

}
