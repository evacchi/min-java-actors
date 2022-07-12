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
import java.lang.System;
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

    <T> Effect<PerformReadLine> reader(Address<PerformReadLine> self, Address<T> addr, Function<String, T> fn, PerformReadLine msg) {
        String line;
        try {
            line = in.readLine();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        if (line != null) {
            addr.tell(fn.apply(line));
            self.tell(new PerformReadLine());
            return Stay();
        } else {
            return Die();
        }
    }

    Effect<WriteLine> writer(WriteLine wl) {
        out.println(wl.payload());
        return Stay();
    }
}
