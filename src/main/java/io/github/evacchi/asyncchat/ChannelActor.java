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

package io.github.evacchi.asyncchat;

import io.github.evacchi.channels.Channels;

import java.nio.charset.StandardCharsets;

import static io.github.evacchi.Actor.*;
import static java.lang.System.out;

interface ChannelActor {
    record LineRead(String payload) {}
    record WriteLine(String payload) {}
    record ReadBuffer(String content) {}

    static Behavior socketHandler(Address self, Address parent, Channels.Socket channel) {
        return socketHandler(self, parent, channel, "");
    }
    private static Behavior socketHandler(Address self, Address clientManager, Channels.Socket channel, String buff) {
        channel.read()
                .thenAccept(s -> self.tell(new ReadBuffer(s)))
                .exceptionally(err -> { err.printStackTrace(); return null; });

        return msg -> switch (msg) {
            case ReadBuffer incoming -> {
                int eol = incoming.content().indexOf('\n');
                if (eol >= 0) {
                    out.println(eol);
                    var line = (buff + incoming.content().substring(0, eol)).trim();
                    clientManager.tell(new LineRead(line));
                    var newBuff = incoming.content().substring(Math.min(eol + 2, incoming.content().length()));
                    yield Become(socketHandler(self, clientManager, channel, newBuff));
                } else {
                    var newBuff = buff + incoming.content();
                    yield Become(socketHandler(self, clientManager, channel, newBuff));
                }
            }
            case WriteLine line -> {
                channel.write((line.payload() + '\n').getBytes(StandardCharsets.UTF_8));
                yield Stay;
            }
            default -> throw new RuntimeException("Unhandled message " + msg);
        };
    }
}
