package io.github.yueeng.hacg.crawler;

import io.github.yueeng.hacg.SearchSite;
import org.jsoup.select.Elements;
import org.junit.Test;

import java.util.List;

import static junit.framework.Assert.assertEquals;

public class SelectGoogle implements Select {

    Rule rule;

    @Override
    public List<LinkTypeData> selectData(Elements results) {
        return null;
    }

    @Override
    public Rule getSelectRule() {
        if (rule == null)
            rule = new Rule("https://www.baidu.com/s?wd=%E7%90%89%E7%92%83%E7%A5%9E%E7%A4%BE%20llss", SearchSite.getParams(),
                    "div[id='1'] .f13 a[class='c-showUrl']",
                    Rule.SELECTION, Rule.GET);
        return rule;
    }

    @Test
    public void test(){
        Rule rule = new Rule("https://www.baidu.com/s?wd=%E7%90%89%E7%92%83%E7%A5%9E%E7%A4%BE%20llss", SearchSite.getParams(),
                "div[id='1'] .f13 a[class='c-showUrl']",
                Rule.SELECTION, Rule.GET);

        ExtractService.setSelect(new SelectGoogle());
        List<LinkTypeData> extracts = ExtractService.extract(rule);

        String url = extracts.get(0).getLinkText();
        url = url.substring(0,url.length()-1);
        assertEquals("www.llss.la",url);
    }
}
