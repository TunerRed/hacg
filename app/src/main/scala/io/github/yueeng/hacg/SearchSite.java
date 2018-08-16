package io.github.yueeng.hacg;

import android.util.Log;
import io.github.yueeng.hacg.crawler.*;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static junit.framework.TestCase.assertEquals;

public class SearchSite implements Runnable{

    private static Map<String,String> params = new HashMap<>();
    private static final int SEARCH_METHODS = 3;
    public static final int SEARCH_LIULISHE = 0;
    public static final int SEARCH_BAIDU = 1;
    public static final int SEARCH_GOOGLE = 2;
    private static int SEARCH_METHOD = SEARCH_LIULISHE;
    private static String[] url;

    public SearchSite(){
        super();
        url = new String[SEARCH_METHODS];
    }

    private void searchSite(int searchMethod){
        Select select = null;
        SEARCH_METHOD = searchMethod;
        switch (SEARCH_METHOD){
            case SEARCH_LIULISHE:select = new SelectLiulishe();break;
            case SEARCH_BAIDU:select = new SelectBaidu();break;
            case SEARCH_GOOGLE:select = new SelectGoogle();break;
            default:select = new SelectLiulishe();break;
        }
        ExtractService.setSelect(select);
        Rule rule = select.getSelectRule();

        try{
            List<LinkTypeData>  extracts = ExtractService.extract(rule);
            if (extracts == null || extracts.size() == 0){
                Log.e("SearchSite","EXTRACTS SIZE "+extracts.size());
                throw new NullPointerException();
            }
            url[SEARCH_METHOD] = extracts.get(0).getLinkHref();
            Log.e("SearchSite","method:"+SEARCH_METHOD+" url:"+url[SEARCH_METHOD]);
            if (SEARCH_METHODS <= SEARCH_METHOD){
                Log.e("SearchSite","too big param for searchMethod");
                throw new NullPointerException();
            }
            if( url[SEARCH_METHOD] == null)
                throw new NullPointerException();
        }catch (NullPointerException ne){
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ne.printStackTrace(new PrintStream(baos));
            String exception = baos.toString();
            Log.e("SearchSite","Search Nothing:"+exception);
        }catch (Exception e){
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            e.printStackTrace(new PrintStream(baos));
            String exception = baos.toString();
            Log.e("SearchSite",exception);
        }

    }

    public static String getUrl(int searchMethod){
        if (SEARCH_METHODS <= searchMethod || url[searchMethod] == null)
            return "Search Nothing";
        return url[searchMethod];
    }

    public static Map<String,String> getParams(){
        if (params == null){
            params.put("User-Agent","Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) " +
                    "Chrome/66.0.3359.139 Safari/537.36");
            params.put("accept","text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,image/apng,*/*;q=0.8");
            params.put("accept-encoding","gzip, deflate, br");
            params.put("accept-language","zh-CN,zh;q=0.9");
        }
        return params;
    }

    @Override
    public void run() {
        searchSite(SEARCH_BAIDU);
        searchSite(SEARCH_LIULISHE);
    }
}
