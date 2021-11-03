package io.github.evacchi.chat;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.evacchi.Actor;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.Socket;
import java.util.function.Consumer;

public interface ChatBehavior {
    static ObjectMapper Mapper = new ObjectMapper();

    static Object Poll = new Object();
    record Message(String user, String text) {}
    record CreateClient(Socket socket) {}

    interface IOConsumer<T> {
        void accept(T t) throws IOException;
        static <T> Consumer<T> of(IOConsumer<T> f) {
            return msg -> {
                try { f.accept(msg); }
                catch (IOException e) { throw new UncheckedIOException(e); }};
        }
    }

    interface IOBehavior {
        Actor.Effect apply(Object msg) throws IOException;

        static Actor.Behavior of(IOBehavior behavior) {
            return msg -> {
                try { return behavior.apply(msg); }
                catch (IOException e) { throw new UncheckedIOException(e); }
            };
        }
    }
}
