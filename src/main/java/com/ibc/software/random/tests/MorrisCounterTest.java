package com.ibc.software.random.tests;

import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;

import java.util.List;
import java.util.Random;
import java.util.stream.IntStream;

public class MorrisCounterTest {

    interface UniformRandomGenerator {
        /**
         * @return a pseudorandom {@code double} value between zero (inclusive)
         *         and one (exclusive)
         */
        double nextDouble();
    }

    interface Counter {
        void inc();
        int getValue();

        default boolean isGreater(Counter other) {
            return getValue() > other.getValue();
        }
    }

    @RequiredArgsConstructor
    static class MorrisCounter implements Counter {
        private static final double[] THRESHOLDS = {1d, 1d/(1<<1), 1d/(1<<2), 1d/(1<<3), 1d/(1<<4), 1d/(1<<5),
                1d/(1<<6), 1d/(1<<7), 1d/(1<<8) };

        private final UniformRandomGenerator randomGenerator;
        byte exp;

        @Override
        public void inc() {
            if (exp == 9) {
                return ; // already at max order of magnitude
            }
            double rand = randomGenerator.nextDouble();
            if (rand < THRESHOLDS[exp]) {
                exp ++;
            }
        }

        @Override
        public int getValue() {
            if (exp == 0) {
                return 0;
            }
            return 1 << (exp - 1);
        }
    }

    static class ActualCounter implements Counter {
        int count = 0;
        @Override
        public void inc() {
            count ++;
        }

        @Override
        public int getValue() {
            return count;
        }
    }

    @RequiredArgsConstructor
    static class DualCounter<K extends Counter, T extends Counter> {
        private final K k;
        private final T t;

        public void inc() {
            k.inc();
            t.inc();
        }

        public boolean isGreaterCorrectValue(DualCounter<K, T> otherCounter) {
            return k.isGreater(otherCounter.k) == t.isGreater(otherCounter.t);
        }

        public String toString() {
            return "[" + k.getClass().getSimpleName() + ":" + k.getValue() + ", " +
                    t.getClass().getSimpleName() + ":" + t.getValue() + "]";
        }
    }

    @SneakyThrows
    public static void main(String[] args) {
        final int TOTAL_COUNTERS = 100_000;
        final int TESTS = 1_000_000;
        final int MAX_COUNTER_VALUE = 512;
        final Random rand = new Random();
        final UniformRandomGenerator randomGenerator = rand::nextDouble;

//        final RandomGenerator genx = RandomGeneratorFactory.of("L64X1024MixRandom").create();
//        final UniformRandomGenerator randomGenerator = genx::nextDouble;

        List<DualCounter<ActualCounter, MorrisCounter>> counters = IntStream.range(0, TOTAL_COUNTERS)
                .map(i -> 1 + rand.nextInt(MAX_COUNTER_VALUE))
                .mapToObj(value -> {
                    DualCounter<ActualCounter, MorrisCounter> dualCounter = new DualCounter<>(new ActualCounter(), new MorrisCounter(randomGenerator));
                    for (int i = 0; i < value; i++) {
                        dualCounter.inc();
                    }
                    return dualCounter;
                }).toList();

        int correctResults = 0;
        for (int t = 0; t < TESTS; t++) {
            int i1 = rand.nextInt(TOTAL_COUNTERS);
            int i2 = rand.nextInt(TOTAL_COUNTERS);
            if (i1 == i2) {
                t--; continue; // retry test;
            }
            if (counters.get(i1).isGreaterCorrectValue(counters.get(i2))) {
                correctResults ++;
            }
        }
        System.out.println(((double)correctResults / TESTS) * 100 + "%");
    }
}
