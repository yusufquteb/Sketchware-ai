package pro.sketchware.utility;

import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class SketchwareUtilTest {

    @Test
    public void testGetRandom() {
        int min = 10;
        int max = 20;
        for (int i = 0; i < 1000; i++) {
            int result = SketchwareUtil.getRandom(min, max);
            assertTrue("Value " + result + " should be >= " + min, result >= min);
            assertTrue("Value " + result + " should be <= " + max, result <= max);
        }
    }

    @Test
    public void testGetRandomSingleValue() {
        int min = 5;
        int max = 5;
        for (int i = 0; i < 100; i++) {
            int result = SketchwareUtil.getRandom(min, max);
            assertTrue("Value should be 5", result == 5);
        }
    }
}
