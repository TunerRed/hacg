package io.github.yueeng.hacg.crawler;

import java.util.LinkedList;
import java.util.Map;

public class Rule {
    public final static int GET = 0 ;
    public final static int POST = 1 ;

    public final static int CLASS = 0;
    public final static int ID = 1;
    public final static int SELECTION = 2;

    public Rule(){}

    public Rule(String url, Map<String,String> headers, String resultTagName, int type, int requestMethod) {
        this.requestMethod = requestMethod;
        this.url = url;

        params = new LinkedList<>();
        values = new LinkedList<>();
        for (String header:headers.keySet()) {
            params.add(header);
            values.add(headers.get(header));
        }

        this.resultTagName = resultTagName;
        this.type = type;
    }

    /**
     *GET / POST
     * 请求的类型，默认GET
     */
    private int requestMethod = GET ;
    private String url;
    private LinkedList<String> params;
    private LinkedList<String> values;

    /**
     * 对返回的HTML，第一次过滤所用的标签，请先设置type
     */
    private String resultTagName;

    /**
     * CLASS / ID / SELECTION
     * 设置resultTagName的类型，默认为ID
     */
    private int type = ID ;


    public int getRequestMethod() {
        return requestMethod;
    }

    public void setRequestMethod(int requestMethod) {
        this.requestMethod = requestMethod;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public LinkedList<String> getParams() {
        return params;
    }

    public void setParams(LinkedList<String> params) {
        this.params = params;
    }

    public LinkedList<String> getValues() {
        return values;
    }

    public void setValues(LinkedList<String> values) {
        this.values = values;
    }

    public String getResultTagName() {
        return resultTagName;
    }

    public void setResultTagName(String resultTagName) {
        this.resultTagName = resultTagName;
    }

    public int getType() {
        return type;
    }

    public void setType(int type) {
        this.type = type;
    }
}
