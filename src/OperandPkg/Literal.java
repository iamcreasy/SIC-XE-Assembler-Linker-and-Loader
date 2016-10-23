package OperandPkg;

/**
 * Each literal is an entry to the Literal Table
 *
 * Literals are addressed 1,2...n. The starting address is a static number,
 * and upon each successful entry to the literal table the static int is increased.
 *
 * Other attributes of literal are public.
 */
public class Literal {
    public static int staticAddress = 1;

    String name, value;
    int length, address;

    /**
     * Helper method that returns formatted attributes of a Literal
     */
    public String toString() {
        String output = String.format("%1$-12s %2$-16s %3$-7d %4$d", name, value, length, address);

        return output;
    }
}
