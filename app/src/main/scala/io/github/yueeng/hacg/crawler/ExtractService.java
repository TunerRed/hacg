package io.github.yueeng.hacg.crawler;

import android.util.Log;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

public class ExtractService
{
    public static Select getSelect() {
        return select;
    }

    public static void setSelect(Select select) {
        ExtractService.select = select;
    }

    private static Select select = null;
    /**
     * @param rule
     * @return
     */
    public static List<LinkTypeData> extract(Rule rule)
    {

        // 进行对rule的必要校验
        validateRule(rule);

        List<LinkTypeData> datas = new ArrayList<LinkTypeData>();
        try
        {
            /**
             * 解析rule
             */
            String url = rule.getUrl();
            LinkedList<String> params = rule.getParams();
            LinkedList<String> values = rule.getValues();
            String resultTagName = rule.getResultTagName();
            int type = rule.getType();
            int requestType = rule.getRequestMethod();

            Connection conn = Jsoup.connect(url);
            // 设置查询参数

            if (params != null)
            {
                for (int i = 0; i < params.size(); i++)
                {
                    conn.data(params.get(i), values.get(i));
                }
            }

            // 设置请求类型
            Document doc = null;
            switch (requestType)
            {
                case Rule.GET:
                    doc = conn.timeout(100000).get();
                    break;
                case Rule.POST:
                    doc = conn.timeout(100000).post();
                    break;
            }

            //System.out.println(doc.html().toString());

            //处理返回数据
            Elements results = new Elements();
            switch (type)
            {
                case Rule.CLASS:
                    results = doc.getElementsByClass(resultTagName);
                    break;
                case Rule.ID:
                    Element result = doc.getElementById(resultTagName);
                    results.add(result);
                    break;
                case Rule.SELECTION:
                    results = doc.select(resultTagName);
                    break;
                default:
                    //当resultTagName为空时默认去body标签
                    if (resultTagName==null || resultTagName.isEmpty())
                    {
                        results = doc.getElementsByTag("body");
                    }
            }

            if (getSelect()== null){
                Log.e("ExService","getSelectNull");
                setSelect(new SelectUrl());
            }
            datas = (List<LinkTypeData>)getSelect().selectData(results);

        } catch (IOException e)
        {
            e.printStackTrace();
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            e.printStackTrace(new PrintStream(baos));
            String exception = baos.toString();
            Log.e("ExtractService","Search Nothing:"+exception);
        }

        return datas;
    }

    /**
     * 对传入的参数进行必要的校验
     */
    private static void validateRule(Rule rule)
    {
        String url = rule.getUrl();
        if (url==null || url.isEmpty())
        {
            throw new RuleException("url不能为空！");
        }
        if (!url.startsWith("http://") && !url.startsWith("https://"))
        {
            throw new RuleException("url的格式不正确！");
        }
    }


}

