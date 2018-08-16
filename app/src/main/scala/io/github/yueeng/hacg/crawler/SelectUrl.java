package io.github.yueeng.hacg.crawler;

import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.util.ArrayList;
import java.util.List;

public class SelectUrl implements Select {
    @Override
    public List<LinkTypeData> selectData(Elements results) {
        return null;
    }

    @Override
    public Rule getSelectRule() {
        return new Rule();
    }

}
