package io.github.evacchi.chat;

import io.github.evacchi.Actor;

import java.util.Scanner;
import java.util.function.Consumer;

import static io.github.evacchi.Actor.Become;
import static io.github.evacchi.Actor.Stay;

public interface ChatBehavior {
    static Object Poll = new Object();
    record Message(String text) {}
    record CreateClient(ChatSocket socket) {}

    /**
     * The behavior of an actor that will always Stay
     */
    static Actor.Behavior staying(Consumer<Object> consumer) {
        return msg -> { consumer.accept(msg); return Stay; };
    }


    /**
     * The behavior of a Staying actor that receives only the Poll message
     */
    static Actor.Behavior loop(Runnable preamble, Consumer<Object> consumer) {
//        self.tell(Poll);
//        scheduler.schedule(() -> self.tell(Poll), 1, TimeUnit.SECONDS);
        preamble.run();
        return msg -> {
            consumer.accept(msg);
            return Become(loop(preamble, consumer));
        };
    }
    /**
     * The behavior of a Poller that tries to read a line every time it receives the Poll message
     */
    static Actor.Behavior lineReader(Runnable preamble, Scanner in, Consumer<String> lineConsumer) {
        return loop(preamble, msg -> {
            if (in.hasNextLine()) {
                var input = in.nextLine();
                lineConsumer.accept(input);
            }
        });
    }
}
