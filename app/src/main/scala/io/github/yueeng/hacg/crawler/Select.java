package io.github.yueeng.hacg.crawler;

import org.jsoup.select.Elements;

import java.util.List;

public interface Select {
    public List<LinkTypeData> selectData(Elements results);
    public Rule getSelectRule();
}
