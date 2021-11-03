package io.github.evacchi.chat;

import io.github.evacchi.Actor;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.Socket;

public interface ChatBehavior {
    static Object Poll = new Object();
    record Message(String text) {}
    record CreateClient(Socket socket) {}

    interface IOBehavior {
        Actor.Effect apply(Object msg) throws IOException;

        static Actor.Behavior of(IOBehavior behavior) {
            return msg -> {
                try {
                    return behavior.apply(msg);
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            };
        }
    }
}
