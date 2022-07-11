//JAVA 19
//JAVAC_OPTIONS --enable-preview --release 19
//JAVA_OPTIONS  --enable-preview
//SOURCES ../../TypedActor.java
package io.github.evacchi.typed.examples;

import io.github.evacchi.TypedActor;

import java.util.concurrent.Executors;

import static io.github.evacchi.TypedActor.*;
import static java.lang.System.out;

public interface VendingMachineAlt {
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
        Address<Vend> vendingMachine = actorSystem.actorOf(VendingMachineAlt::initial);
        vendingMachine
                .tell(new Coin(50))
                .tell(new Coin(40))
                .tell(new Coin(30))
                .tell(new Choice("Chocolate"));
    }
    static Behavior<Vend> initial(Address<Vend> self) {
        return message -> {
            if (message instanceof Coin c) {
                out.println("Received first coin: " + c.amount);
                return Become(waitCoin(self, c.amount()));
            } else return Stay(); // ignore message, stay in this state
        };
    }
    static Behavior<Vend> waitCoin(Address<Vend> self, int counter) {
        return message -> switch(message) {
            case Coin c when counter + c.amount() < 100 -> {
                var count = counter + c.amount();
                out.println("Received coin: " + count + " of 100");
                yield Become(waitCoin(self, count));
            }
            case Coin c -> {
                var count = counter + c.amount();
                out.println("Received last coin: " + count + " of 100");
                var change = counter + c.amount() - 100;
                yield Become(vend(self, change));
            }
            default -> Stay(); // ignore message, stay in this state
        };
    }
    static Behavior<Vend> vend(Address<Vend> self, int change) {
        return message -> switch(message) {
            case Choice c -> {
                vendProduct(c.product());
                releaseChange(change);
                yield Become(initial(self));
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
