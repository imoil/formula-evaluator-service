package com.imoil;

import java.util.ArrayList;
import java.util.List;

public class Benchmark {
    public static void main(String[] args) {
        int itemCount = 1000;
        List<FormulaEvaluator.Item> items = new ArrayList<>();
        for (int i = 0; i < itemCount; i++) {
            items.add(new FormulaEvaluator.Item("id_" + i));
        }

        MockDatabase db = new MockDatabase(1); // 1ms delay per call
        FormulaEvaluator evaluator = new FormulaEvaluator(db);

        System.out.println("Starting N+1 benchmark with " + itemCount + " items...");
        long startTime = System.currentTimeMillis();
        evaluator.evaluate(items);
        long endTime = System.currentTimeMillis();
        System.out.println("N+1 Execution Time: " + (endTime - startTime) + "ms");
        System.out.println("Total database calls: " + db.getCallCount());
    }

    static class MockDatabase implements FormulaEvaluator.Database {
        private final int delay;
        private int callCount = 0;

        public MockDatabase(int delay) {
            this.delay = delay;
        }

        @Override
        public void query(FormulaEvaluator.Item item) {
            callCount++;
            try {
                Thread.sleep(delay);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        @Override
        public void queryBatch(List<FormulaEvaluator.Item> items) {
            callCount++;
            try {
                Thread.sleep(delay);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        public int getCallCount() {
            return callCount;
        }
    }
}
