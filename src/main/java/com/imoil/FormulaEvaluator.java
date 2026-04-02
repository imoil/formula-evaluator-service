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

    public interface Database {
        void query(Item item);
        void queryBatch(List<Item> items);
    }

    public static class Item {
        private final String id;
        public Item(String id) { this.id = id; }
        public String getId() { return id; }
    }
}
