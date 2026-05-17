package cn.clientbase.util.misc;

import lombok.experimental.UtilityClass;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;

@UtilityClass
public class MathUtil {

    public float getRandomInRange(float min, float max) {
        SecureRandom random = new SecureRandom();
        return random.nextFloat() * (max - min) + min;
    }

    public long getRandomInRange(long min, long max) {
        SecureRandom random = new SecureRandom();
        return (long) (random.nextFloat() * (max - min) + min);
    }
}
