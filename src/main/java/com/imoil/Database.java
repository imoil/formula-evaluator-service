package com.imoil;

import java.util.List;

public interface Database {
    void query(Item item);
    void queryBatch(List<Item> items);
}
