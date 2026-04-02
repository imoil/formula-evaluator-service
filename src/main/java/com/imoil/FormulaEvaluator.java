package com.imoil;

import java.util.List;

public class FormulaEvaluator {
    private final Database db;

    public FormulaEvaluator(Database db) {
        this.db = db;
    }

    public void evaluate(List<Item> items) {
        if (items != null && !items.isEmpty()) {
            db.queryBatch(items);
        }
    }
}
