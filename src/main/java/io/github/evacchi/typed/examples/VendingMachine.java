//JAVA 17
//JAVAC_OPTIONS --enable-preview --release 17
//JAVA_OPTIONS  --enable-preview
//SOURCES ../../TypedActor.java
package io.github.evacchi.typed.examples;

import io.github.evacchi.TypedActor;

import java.util.concurrent.Executors;

import static io.github.evacchi.TypedActor.*;
import static java.lang.System.out;

interface VendMessage {}

record Coin(int amount) implements VendMessage {
    public Coin {
        if (amount < 1 && amount > 100)
            throw new AssertionError("1 <= amount < 100");
    }
}

record Vended(String product) implements VendMessage {}

public class VendingMachine {
    static record Choice(String product) implements VendMessage {}

    TypedActor.System sys = new TypedActor.System(Executors.newCachedThreadPool());

    Address<VendMessage> vendingMachine = sys.actorOf(self -> initial(self));
    Address<Choice> itemPicker = sys.actorOf(self -> msg -> itemPicker(msg));

    public static void main(String... args) {
        new VendingMachine().vendingMachine
                .tell(new Coin(50))
                .tell(new Coin(40))
                .tell(new Coin(30))
                .tell(new Choice("Chocolate"));
    }

    Behavior<VendMessage> initial(Address<VendMessage> self) {
        return message -> {
            if (message instanceof Coin c) {
                out.printf("Received first coin: %d\n", c.amount());
                return Become(waitCoin(self, c.amount()));
            } else return Stay(); // ignore message, stay in this state
        };
    }

    Behavior<VendMessage> waitCoin(Address<VendMessage> self, int accumulator) {
        out.printf("Budget updated: %d\n", accumulator);
        return m -> switch (m) {
            case Coin c && accumulator + c.amount() < 100 ->
                    Become(waitCoin(self, accumulator + c.amount()));
            case Coin c ->
                    Become(vend(self, accumulator + c.amount()));
            default -> Stay();
        };
    }
    Behavior<VendMessage> vend(Address<VendMessage> self, int total) {
        out.printf("Pick an Item! (Budget: %d)\n", total);
        return message -> switch(message) {
            case Choice c -> {
                itemPicker.tell(c);
                releaseChange(total - 100);
                yield Stay();
            }
            case Vended v -> Become(initial(self));
            default -> Stay(); // ignore message, stay in this state
        };
    }

    Effect<Choice> itemPicker(Choice message) {
        vendProduct(message.product());
        vendingMachine.tell(new Vended(message.product()));
        return Stay();
    }

    void vendProduct(String product) {
        out.printf("VENDING: %s\n", product);
    }

    void releaseChange(int change) {
        out.printf("CHANGE: %s\n", %d);
    }

}
