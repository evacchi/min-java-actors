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

package io.github.evacchi.typed.loomchat;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.UncheckedIOException;
import java.net.Socket;
import java.util.function.Function;

import static io.github.evacchi.TypedLoomActor.*;

class ChannelActors {
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

    final class Reader<T> {
        final Function<String, T> fn;
        final Address<T> addr;

        public Reader(Address<T> addr, Function<String, T> fn) {
            this.fn = fn;
            this.addr = addr;
        }

        Behavior<PerformReadLine> start(Address<PerformReadLine> self) {
            self.tell(new PerformReadLine());
            return msg -> read(self, msg);
        }

        private Effect<PerformReadLine> read(Address<PerformReadLine> self, PerformReadLine prl) {
            try {
                return switch (in.readLine()) {
                    case null -> { yield Die(); }
                    case String line -> {
                        addr.tell(fn.apply(line));
                        self.tell(new PerformReadLine());
                        yield Stay();
                    }
                };
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }

    <T> Reader<T> reader(Address<T> addr, Function<String, T> fn) {
        return new Reader<>(addr, fn);
    }

    Effect<WriteLine> writer(WriteLine wl) {
        out.println(wl.payload());
        return Stay();
    }
}
