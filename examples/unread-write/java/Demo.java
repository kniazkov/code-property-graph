/* Code Property Graph: an overwritten initializer and a live replacement. MIT License. */
/** Demonstrates the unread-write diagnostic on the initialization of score. */
public class Demo {
    public static void main(String[] args) {
        int score = 1;
        score = 2;
        System.out.println(score);
    }
}
