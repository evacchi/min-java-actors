/*
 *    Copyright 2021 Andrea Peruffo
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

package io.github.evacchi.loomchat;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.UncheckedIOException;
import java.net.Socket;

import static io.github.evacchi.LoomActor.*;

class ChannelActors {
    record LineRead(String payload) {}
    record WriteLine(String payload) {}

    record PerformReadLine() {}

    final BufferedReader in;
    final PrintWriter out;

    ChannelActors(Socket socket) {
        try {
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            out = new PrintWriter(socket.getOutputStream(), true);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    Behavior reader(Address self, Address addr) {
        self.tell(new PerformReadLine());

        return msg -> {
            switch(msg) {
                case PerformReadLine prl -> {
                    try {
                        return switch (in.readLine()) {
                            case null -> { yield Die; }
                            case String line -> {
                                addr.tell(new LineRead(line));
                                self.tell(new PerformReadLine());
                                yield Stay;
                            }
                        };
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                }
                default -> throw new RuntimeException("Unhandled message " + msg);
            }
        };
    }

    Behavior writer() {
        return msg -> {
            switch (msg) {
                case WriteLine(var payload) -> {
                    out.println(payload);
                }
                default -> throw new RuntimeException("Unhandled message " + msg);
            }
            return Stay;
        };
    }
}
