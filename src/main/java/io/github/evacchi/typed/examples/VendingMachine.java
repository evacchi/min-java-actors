//JAVA 17
//JAVAC_OPTIONS --enable-preview --release 17
//JAVA_OPTIONS  --enable-preview
//SOURCES ../../TypedActor.java
package io.github.evacchi.typed.examples;

import io.github.evacchi.TypedActor;

import java.util.concurrent.Executors;

import static io.github.evacchi.TypedActor.*;
import static java.lang.System.out;

public interface VendingMachine {
    sealed interface Vend {}
    static record Coin(int amount) implements Vend {
        public Coin {
            if (amount < 1 && amount > 100)
                throw new AssertionError("1 <= amount < 100");
        }
    }
    static record Choice(String product) implements Vend {}

    static void main(String... args) {
        var actorSystem = new TypedActor.System(Executors.newCachedThreadPool());
        var vendingMachine = actorSystem.actorOf((Address<Vend> self) -> (Vend msg) -> initial(msg));
        vendingMachine
                .tell(new Coin(50))
                .tell(new Coin(40))
                .tell(new Coin(30))
                .tell(new Choice("Chocolate"));
    }
    static Effect<Vend> initial(Vend message) {
        return switch(message) {
            case Coin c -> {
                out.println("Received first coin: " + c.amount);
                yield Become(m -> waitCoin(m, c.amount()));
            }
            default -> Stay(); // ignore message, stay in this state
        };
    }
    static Effect<Vend> waitCoin(Vend message, int counter) {
        return switch(message) {
            case Coin c && counter + c.amount() < 100 -> {
                var count = counter + c.amount();
                out.println("Received coin: " + count + " of 100");
                yield Become(m -> waitCoin(m, count));
            }
            case Coin c -> {
                var count = counter + c.amount();
                out.println("Received last coin: " + count + " of 100");
                var change = counter + c.amount() - 100;
                yield Become(m -> vend(m, change));
            }
            default -> Stay(); // ignore message, stay in this state
        };
    }
    static  Effect<Vend> vend(Vend message, int change) {
        return switch(message) {
            case Choice c -> {
                vendProduct(c.product());
                releaseChange(change);
                yield Become(m -> initial(m));
            }
            default -> Stay(); // ignore message, stay in this state
        };
    }


    static void vendProduct(String product) {
        out.println("VENDING: " + product);
    }

    static  void releaseChange(int change) {
        out.println("CHANGE: " + change);
    }

}
