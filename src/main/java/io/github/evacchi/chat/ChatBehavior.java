package io.github.evacchi.chat;

import io.github.evacchi.Actor;

import java.util.Scanner;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import static io.github.evacchi.Actor.Stay;

public interface ChatBehavior {
    static Object Poll = new Object();
    static Actor.Behavior staying(Consumer<Object> consumer) {
        return msg -> { consumer.accept(msg); return Stay; };
    }

    static Actor.Behavior poller(Actor.Address self, ScheduledExecutorService scheduler, Consumer<Object> consumer) {
        self.tell(Poll);
        return staying(msg -> {
            consumer.accept(msg);
            scheduler.schedule(() -> self.tell(Poll), 1, TimeUnit.SECONDS);
        });
    }
    static Actor.Behavior lineReader(Actor.Address self, Scanner in, ScheduledExecutorService scheduler, Consumer<String> lineConsumer) {
        return poller(self, scheduler, msg -> {
            if (in.hasNextLine()) {
                var input = in.nextLine();
                lineConsumer.accept(input);
            }
        });
    }
}
