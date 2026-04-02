package com.imoil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class FormulaEvaluatorTest {
    public static void main(String[] args) {
        testEvaluate();
        testEvaluateEmpty();
        testEvaluateNull();
        testEvaluateSingle();
        testEvaluatePartitioning();
        System.out.println("All tests passed!");
    }

    private static void testEvaluate() {
        MockDatabase db = new MockDatabase();
        FormulaEvaluator evaluator = new FormulaEvaluator(db);
        List<Item> items = Arrays.asList(
            new Item("1"),
            new Item("2")
        );
        evaluator.evaluate(items);
        if (db.batchCalledCount != 1) {
            throw new RuntimeException("Expected 1 batch call, but got " + db.batchCalledCount);
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

    private static void testEvaluateSingle() {
        MockDatabase db = new MockDatabase();
        FormulaEvaluator evaluator = new FormulaEvaluator(db);
        List<Item> items = Arrays.asList(new Item("1"));
        evaluator.evaluate(items);
        if (db.batchCalledCount != 1) {
            throw new RuntimeException("Expected 1 batch call for single item list, but got " + db.batchCalledCount);
        }
    }

    private static void testEvaluatePartitioning() {
        MockDatabase db = new MockDatabase();
        FormulaEvaluator evaluator = new FormulaEvaluator(db);
        List<Item> items = new ArrayList<>();
        for (int i = 0; i < 250; i++) {
            items.add(new Item("id_" + i));
        }
        evaluator.evaluate(items);
        // BATCH_SIZE is 100, so for 250 items, we expect 3 batch calls (100, 100, 50)
        if (db.batchCalledCount != 3) {
            throw new RuntimeException("Expected 3 batch calls for 250 items (batch size 100), but got " + db.batchCalledCount);
        }
    }

    static class MockDatabase implements Database {
        int batchCalledCount = 0;

        @Override
        public void query(Item item) {}

        @Override
        public void queryBatch(List<Item> items) {
            batchCalledCount++;
        }
    }
}
