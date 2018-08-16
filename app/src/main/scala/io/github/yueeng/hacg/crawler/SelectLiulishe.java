package io.github.yueeng.hacg.crawler;

import io.github.yueeng.hacg.SearchSite;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

public class SelectLiulishe implements Select {

    Rule rule;

    @Override
    public List<LinkTypeData> selectData(Elements results) {
        String text = null;
        Pattern pattern = Pattern.compile("href=\\\\\"http://[a-zA-Z]+\\.[a-zA-Z]+/wp");
        List<LinkTypeData> datas = new ArrayList<LinkTypeData>();
        LinkTypeData data = null;
        for (Element result : results)
        {
            //必要的筛选
            data = new LinkTypeData();
            Matcher matcher = pattern.matcher(result.html());
            if(matcher.find()){
                text = matcher.group();
                text = "www."+text.substring("href=\\\"http://".length(),text.indexOf("/wp"));
                data.setLinkHref(text);
                datas.add(data);
                break;
            }
        }
        return datas;
    }

    @Override
    public Rule getSelectRule() {
        if (rule == null)
            rule = new Rule("http://liulishe.me", SearchSite.getParams(),
                    "script:containsData(ps)",
                    Rule.SELECTION, Rule.GET);
        return rule;
    }

    @Test
    public void test(){
        Rule rule = new Rule("http://liulishe.me", SearchSite.getParams(),
                "script:containsData(ps)",
                Rule.SELECTION, Rule.GET);
        //:contains('Ember')not([type],[src])contains('function')

        ExtractService.setSelect(new SelectLiulishe());
        List<LinkTypeData> extracts = ExtractService.extract(rule);

        assertEquals(2,extracts.size());

        String text = null;
        Pattern pattern = Pattern.compile("href=\\\\\"http://[a-zA-Z]+\\.[a-zA-Z]+/wp");

        for (int i = 0; i < extracts.size(); i++){
            Matcher matcher = pattern.matcher("href=\\\"http://llss.li/wp");
            if(matcher.find()){
                text = matcher.group();
                break;
            }
        }
        assertNotNull(text);
        text = text.substring("href=\\\"http://".length(),text.indexOf("/wp"));
        assertEquals("llss.li",text);
    }
}
