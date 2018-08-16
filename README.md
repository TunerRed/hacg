# Hacg

![HACG](https://raw.githubusercontent.com/yueeng/hacg/master/app/src/main/res/mipmap-xxxhdpi/ic_launcher.png)

这个是为了学习新Android库和Scala写的练习项目，应用的所有内容全部来自hacg.me。

发布：https://github.com/yueeng/hacg/releases

![HACG](https://user-images.githubusercontent.com/4374375/33003955-f2ed932c-cdf8-11e7-961c-8a7a536e0fd5.png)

首页 | 内容 | 评论
------------ | ------------- | -------------
![screenshot01](https://cloud.githubusercontent.com/assets/4374375/8587179/e53cab82-262a-11e5-8edf-da067e7e4494.png)|![screenshot02](https://cloud.githubusercontent.com/assets/4374375/8587180/e540b1c8-262a-11e5-91c9-ded4d0a94d93.png)|![screenshot03](https://cloud.githubusercontent.com/assets/4374375/8587178/e4f8ade2-262a-11e5-9734-e227a09f034d.png)

***
# 琉璃神社·改

![HACG](https://github.com/TunerRed/hacg/blob/tunerred/app/src/main/res/mipmap-xhdpi/ic_launcher.png?raw=true)
***
__基于琉璃神社ver1.2.6__

## 部署
- JDK 1.8
- Scala 2.12.6
- sbt 0.13

## 添加的功能与特色
- **代码尽可能添加注释**
- 评论界面设置为黑色背景
- 神社添加夜间模式
    - 多数情况调低亮度并不会改善夜间浏览效果，因此添加了遮罩
    - 夜间模式不会保存，退出重进默认是白天模式
- 文章详情内的获取磁力只点击一次即可触发
- 从百度、琉璃社获取神社网址
    - 一般来讲都是盗版网址
    - 搜索APP启动时初始化并确定URL
    - 获取的URL复制到系统粘贴板
- 添加推荐内容链接
    - 网页版的推荐还是不错的，不清楚为什么要去掉
    - 点击推荐内容会使用浏览器打开~~懒得做了~~
    - 推荐内容的图片缩小，不再占用100%的屏幕宽度
- 对于盗版网址的处理办法
    - 去除顶部和底部显示的广告
    - 优化推荐内容的显示
    - 分目录浏览解决列表加载问题
- 修改APP的名称为**琉璃神社·改**，换用了部分自己喜欢的默认图片（唔姆！）

## 对于盗版网址的说明
- 之前使用APP时由于云端网址等数据过旧，会导致APP无法加载或及时更新的烦恼，或是偶尔找到一个网址却无法**使用**的问题，而盗版网站内的网页结构与正版相似，因此可以直接拿来处理，在项目内添加爬虫和配置文件用来支持对这些网站的解析
- 但是仍然**不推荐使用盗版网址**~~虽然有时更新赶超正版~~,尽可能支持正版，该版本只是为了满足个人需求，以及暑期略无聊的境地


## 个人杂谈
- Scala写安卓的例子在网上是真的难找，环境都难配，到最后发现基本是安卓用java，java替换为Scala。不过异步加载的地方似乎还是挺简洁的？
- 大佬写代码加注释啊喂
<img src="https://github.com/TunerRed/hacg/blob/tunerred/screenshot.jpg?raw=true" width=216 height=384 />
