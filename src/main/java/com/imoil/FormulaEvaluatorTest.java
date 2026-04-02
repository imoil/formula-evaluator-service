package com.imoil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class FormulaEvaluatorTest {
    public static void main(String[] args) {
        testEvaluate();
        testEvaluateEmpty();
        testEvaluateNull();
        System.out.println("All tests passed!");
    }

    private static void testEvaluate() {
        MockDatabase db = new MockDatabase();
        FormulaEvaluator evaluator = new FormulaEvaluator(db);
        List<FormulaEvaluator.Item> items = Arrays.asList(
            new FormulaEvaluator.Item("1"),
            new FormulaEvaluator.Item("2")
        );
        evaluator.evaluate(items);
        if (db.batchCalledCount != 1) {
            throw new RuntimeException("Expected 1 batch call, but got " + db.batchCalledCount);
        }
        if (db.lastItems != items) {
            throw new RuntimeException("Expected items to be passed to batch call");
        }
    }

    private static void testEvaluateEmpty() {
        MockDatabase db = new MockDatabase();
        FormulaEvaluator evaluator = new FormulaEvaluator(db);
        evaluator.evaluate(new ArrayList<>());
        if (db.batchCalledCount != 0) {
            throw new RuntimeException("Expected 0 batch calls for empty list, but got " + db.batchCalledCount);
        }
    }

    private static void testEvaluateNull() {
        MockDatabase db = new MockDatabase();
        FormulaEvaluator evaluator = new FormulaEvaluator(db);
        evaluator.evaluate(null);
        if (db.batchCalledCount != 0) {
            throw new RuntimeException("Expected 0 batch calls for null list, but got " + db.batchCalledCount);
        }
    }

    static class MockDatabase implements FormulaEvaluator.Database {
        int batchCalledCount = 0;
        List<FormulaEvaluator.Item> lastItems;

        @Override
        public void query(FormulaEvaluator.Item item) {}

        @Override
        public void queryBatch(List<FormulaEvaluator.Item> items) {
            batchCalledCount++;
            lastItems = items;
        }
    }
}
