package com.imoil;

import java.util.ArrayList;
import java.util.List;

public class Benchmark {
    private static final int ITEM_COUNT = 1000;
    private static final int ITERATIONS = 5;
    private static final int WARMUP_ITERATIONS = 2;

    public static void main(String[] args) {
        List<Item> items = new ArrayList<>();
        for (int i = 0; i < ITEM_COUNT; i++) {
            items.add(new Item("id_" + i));
        }

        System.out.println("--- Starting Benchmark (Item Count: " + ITEM_COUNT + ") ---");

        // Benchmark N+1 pattern (Conceptual - using a manual loop)
        long nPlusOneTotalTime = 0;
        for (int i = 0; i < ITERATIONS + WARMUP_ITERATIONS; i++) {
            MockDatabase db = new MockDatabase(1); // 1ms delay per call
            long startTime = System.nanoTime();
            for (Item item : items) {
                db.query(item);
            }
            long endTime = System.nanoTime();
            if (i >= WARMUP_ITERATIONS) {
                nPlusOneTotalTime += (endTime - startTime);
            }
        }
        double nPlusOneAvgMs = (nPlusOneTotalTime / (double) ITERATIONS) / 1_000_000.0;
        System.out.printf("Baseline (N+1) Avg Execution Time: %.2f ms\n", nPlusOneAvgMs);

        // Benchmark Optimized pattern (Batch Partitioned)
        long optimizedTotalTime = 0;
        for (int i = 0; i < ITERATIONS + WARMUP_ITERATIONS; i++) {
            MockDatabase db = new MockDatabase(1); // 1ms delay per call (batch)
            FormulaEvaluator evaluator = new FormulaEvaluator(db);
            long startTime = System.nanoTime();
            evaluator.evaluate(items);
            long endTime = System.nanoTime();
            if (i >= WARMUP_ITERATIONS) {
                optimizedTotalTime += (endTime - startTime);
            }
        }
        double optimizedAvgMs = (optimizedTotalTime / (double) ITERATIONS) / 1_000_000.0;
        System.out.printf("Optimized (Batch Partitioned) Avg Execution Time: %.2f ms\n", optimizedAvgMs);

        System.out.printf("Performance Gain: %.2f%%\n", (1.0 - optimizedAvgMs / nPlusOneAvgMs) * 100);
    }

    static class MockDatabase implements Database {
        private final int delay;

        public MockDatabase(int delay) {
            this.delay = delay;
        }

        @Override
        public void query(Item item) {
            simulateDelay();
        }

        @Override
        public void queryBatch(List<Item> items) {
            simulateDelay();
        }

        private void simulateDelay() {
            try {
                Thread.sleep(delay);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
