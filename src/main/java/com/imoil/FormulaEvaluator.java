package com.imoil;

import java.util.List;

public class FormulaEvaluator {
    private static final int BATCH_SIZE = 100;
    private final Database db;

    public FormulaEvaluator(Database db) {
        this.db = db;
    }

    public void evaluate(List<Item> items) {
        if (items == null || items.isEmpty()) {
            return;
        }

        int totalItems = items.size();
        for (int i = 0; i < totalItems; i += BATCH_SIZE) {
            int end = Math.min(i + BATCH_SIZE, totalItems);
            List<Item> batch = items.subList(i, end);
            db.queryBatch(batch);
        }
    }
}
