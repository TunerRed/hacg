package io.github.yueeng.hacg

import java.text.SimpleDateFormat
import java.util.concurrent.Future

import android.app.DownloadManager
import android.app.DownloadManager.Request
import android.content.DialogInterface.OnShowListener
import android.content._
import android.graphics.PixelFormat
import android.net.Uri
import android.os.{Build, Bundle}
import android.preference.PreferenceManager
import android.support.v4.app.Fragment
import android.support.v4.content.ContextCompat
import android.support.v4.view.{PagerAdapter, ViewPager}
import android.support.v4.widget.SwipeRefreshLayout
import android.support.v4.widget.SwipeRefreshLayout.OnRefreshListener
import android.support.v7.app.AlertDialog.Builder
import android.support.v7.app.{AlertDialog, AppCompatActivity}
import android.support.v7.widget.{LinearLayoutManager, RecyclerView}
import android.view.ViewGroup.LayoutParams
import android.view._
import android.webkit._
import android.widget._
import com.github.clans.fab.{FloatingActionButton, FloatingActionMenu}
import com.squareup.picasso.Picasso
import io.github.yueeng.hacg.Common._
import io.github.yueeng.hacg.ViewBinder.{ErrorBinder, ViewBinder}

import scala.collection.JavaConversions._

/**
  * Info activity
  * Created by Rain on 2015/5/12.
  */

class InfoActivity extends BaseSlideCloseActivity {
  lazy val _article: Article = getIntent.getParcelableExtra[Article]("article")

  var mWindowManager:WindowManager = null
  var tv:TextView = null

  override def onCreate(savedInstanceState: Bundle): Unit = {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_info)
    val manager = getSupportFragmentManager

    if (NightMode.isNight){
      mWindowManager = getSystemService(Context.WINDOW_SERVICE).asInstanceOf[WindowManager]
      tv = new TextView(InfoActivity.this)
      tv.setBackgroundColor(0x99000000)
      var lp = new WindowManager.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.TYPE_APPLICATION,
        WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.TRANSLUCENT)
      lp.gravity = Gravity.BOTTOM // 可以自定义显示的位置
      lp.y = 10 // 距离底部的距离是10像素 如果是 top 就是距离top是10像素
      mWindowManager.addView(tv, lp)
    }

    val fragment = manager.findFragmentById(R.id.container) match {
      case fragment: InfoFragment => fragment
      case _ => new InfoFragment().arguments(new Bundle().parcelable("article", _article))
    }
    manager.beginTransaction().replace(R.id.container, fragment).commit()
  }

  /*
  * 返回键
  * 包括从评论返回和从信息界面返回
  * */
  override def onBackPressed(): Unit = {
    getSupportFragmentManager.findFragmentById(R.id.container) match {
      case fragment: InfoFragment if fragment.onBackPressed =>
      case _ => {
        super.onBackPressed();
        if (NightMode.isNight)
          mWindowManager.removeViewImmediate(tv);
        mWindowManager = null;
        tv = null;
      }
    }
  }

  /*
  * 个人重写的滑动关闭窗口的调用
  * 为了避免内存泄露
  * 关闭窗口时还需要处理夜间模式所用的textView句柄
  * */
  override def onPanelOpened(panel: View): Unit = {
    finish()
    if (NightMode.isNight)
      mWindowManager.removeViewImmediate(tv);
    mWindowManager = null;
    tv = null;
  }

  /*
  * 菜单栏的返回按钮
  * 会调用onBackPressed，不需要过多处理
  * */
  override def onOptionsItemSelected(item: MenuItem): Boolean = {
    item.getItemId match {
      case android.R.id.home => onBackPressed(); true
      case _ => super.onOptionsItemSelected(item)
    }
  }
}

/*
* 似乎是显示文章内容的主窗体
* */
class InfoFragment extends Fragment {
  lazy val _article: Article = getArguments.getParcelable[Article]("article")
  lazy val _adapter = new CommentAdapter
  val _web = new ViewBinder[(String, String), WebView](null)((view, value) => view.loadDataWithBaseURL(value._2, value._1, "text/html", "utf-8", null))
  val _error = new ErrorBinder(false) {
    override def retry(): Unit = query(_article.link, QUERY_ALL)
  }
  val _post = new scala.collection.mutable.HashMap[String, String]
  var _url: String = _

  //评论功能内点击评论触发的监听器，弹出回复窗口
  val _click: View.OnClickListener = viewClick {
    _.getTag match {
      case c: Comment => comment(c)
      case _ =>
    }
  }

  val CONFIG_AUTHOR = "config.author"
  val CONFIG_EMAIL = "config.email"
  val AUTHOR = "author"
  val EMAIL = "email"
  var COMMENTURL = ""
  var COMMENT = "comment"
  val COMMENTPREFIX = "comment-[a-f0-9]{8}"

  //一系列设置后调用query设置文章内容
  override def onCreate(savedInstanceState: Bundle): Unit = {
    super.onCreate(savedInstanceState)
    setHasOptionsMenu(true)
    setRetainInstance(true)
    val preference = PreferenceManager.getDefaultSharedPreferences(getActivity)
    _post += (AUTHOR -> preference.getString(CONFIG_AUTHOR, ""), EMAIL -> preference.getString(CONFIG_EMAIL, ""))
    query(_article.link, QUERY_ALL)
  }

  //文章中附带的磁力链接？
  lazy val _magnet = new ViewBinder[List[String], View](Nil)((view, value) => view.setVisibility(if (value.nonEmpty) View.VISIBLE else View.GONE))

  lazy val _progress = new ViewBinder[Boolean, ProgressBar](false)((view, value) => {
    view.setIndeterminate(value)
    view.setVisibility(if (value) View.VISIBLE else View.INVISIBLE)
  })

  lazy val _progress2 = new ViewBinder[Boolean, SwipeRefreshLayout](false)((view, value) => view.post(() => view.setRefreshing(value)))

  //注销窗体
  override def onDestroy(): Unit = {
    super.onDestroy()
    _web.views.foreach(_.destroy())
  }

  //布局文件：fragment_info.xml
  override def onCreateView(inflater: LayoutInflater, container: ViewGroup, savedInstanceState: Bundle): View =
    inflater.inflate(R.layout.fragment_info, container, false)

  //创建界面，加载上方的返回按钮、图标和文章标题
  override def onViewCreated(view: View, state: Bundle): Unit = {
    val root = view

    val activity = getActivity.asInstanceOf[AppCompatActivity]
    activity.setSupportActionBar(root.findViewById(R.id.toolbar))
    activity.getSupportActionBar.setLogo(R.mipmap.ic_launcher)
    activity.getSupportActionBar.setDisplayHomeAsUpEnabled(true)
    activity.setTitle(_article.title)
    /*
    * fragment_info.xml中的container组件显示核心信息
    * 加载方式见InfoAdapter
    * */
    root.findViewById[ViewPager](R.id.container).setAdapter(new InfoAdapter)
  }

  class InfoAdapter extends PagerAdapter {
    override def getCount: Int = 2

    override def isViewFromObject(view: View, `object`: scala.Any): Boolean = view == `object`

    override def destroyItem(container: ViewGroup, position: Int, `object`: scala.Any): Unit = {
      container.removeView(`object`.asInstanceOf[View])
    }


    override def instantiateItem(container: ViewGroup, position: Int): AnyRef = (position match {
        /*
        * 详情页
        * 1.加载右下角按钮组、获取按钮必要信息并注册事件
        * 2.
        * */
      case 0 => container.inflate(R.layout.fragment_info_web).also { root =>
        _error += root.findViewById(R.id.image1)
        /*详情页右下角按钮组*/
        val menu: FloatingActionMenu = root.findViewById(R.id.menu1)
        menu.setMenuButtonColorNormal(randomColor())
        menu.setMenuButtonColorPressed(randomColor())
        menu.setMenuButtonColorRipple(randomColor())
        val click = viewClick { v =>
          //若是这三个按钮，则执行操作后收起（右下角的按钮）
          /*
          * 1.浏览器打开
          * 2.评论
          * 4.分享
          * */
          v.getId match {
            case R.id.button1 => Common.openWeb(getActivity, _article.link)
            case R.id.button2 => getView.findViewById[ViewPager](R.id.container).setCurrentItem(1)
            case R.id.button4 => share(_article.image)
          }
          getView.findViewById[FloatingActionMenu](R.id.menu1).close(true)
        }
        //为按钮注册监听器
        List(R.id.button1, R.id.button2, R.id.button4).map(root.findViewById[View]).foreach {
          case b: FloatingActionButton =>
            b.setOnClickListener(click)
          case _ =>
        }

        _progress += root.findViewById(R.id.progress)
        _magnet += root.findViewById(R.id.button5)
        _magnet.views.head.setOnClickListener(new View.OnClickListener {
          val max = 3
          var magnet = 0
          var toast: Toast = _

          /*
          * 复制磁力或网盘链接
          * 去掉了需要点击三次的操作
          * */
          override def onClick(v: View): Unit = {
            if( _magnet() != null && _magnet().nonEmpty){
              new Builder(getActivity)
                .setTitle(R.string.app_magnet)
                .setSingleChoiceItems(_magnet().map(m => s"${if (m.contains(",")) "baidu" else "magnet"}:$m").toArray[CharSequence], 0, null)
                .setNegativeButton(R.string.app_cancel, null)
                .setPositiveButton(R.string.app_open, dialogClick { (d, _) =>
                  val pos = d.asInstanceOf[AlertDialog].getListView.getCheckedItemPosition
                  val item = _magnet()(pos)
                  val link = if (item.contains(",")) {
                    val baidu = item.split(",")
                    clipboard(getString(R.string.app_magnet), baidu.last)
                    s"https://yun.baidu.com/s/${baidu.head}"
                  } else s"magnet:?xt=urn:btih:${_magnet()(pos)}"
                  startActivity(Intent.createChooser(new Intent(Intent.ACTION_VIEW, Uri.parse(link)), getString(R.string.app_magnet)))
                }).setNeutralButton(R.string.app_copy, dialogClick { (d, _) =>
                val pos = d.asInstanceOf[AlertDialog].getListView.getCheckedItemPosition
                val item = _magnet()(pos)
                val link = if (item.contains(",")) s"https://yun.baidu.com/s/${item.split(",").head}" else s"magnet:?xt=urn:btih:${_magnet()(pos)}"
                clipboard(getString(R.string.app_magnet), link)
              }).create().show()
              menu.close(true)
            }
          }

           /* magnet match {
            case `max`
              if _magnet() != null && _magnet().nonEmpty => new Builder(getActivity)
              .setTitle(R.string.app_magnet)
              .setSingleChoiceItems(_magnet().map(m => s"${if (m.contains(",")) "baidu" else "magnet"}:$m").toArray[CharSequence], 0, null)
              .setNegativeButton(R.string.app_cancel, null)
              .setPositiveButton(R.string.app_open, dialogClick { (d, _) =>
                val pos = d.asInstanceOf[AlertDialog].getListView.getCheckedItemPosition
                val item = _magnet()(pos)
                val link = if (item.contains(",")) {
                  val baidu = item.split(",")
                  clipboard(getString(R.string.app_magnet), baidu.last)
                  s"https://yun.baidu.com/s/${baidu.head}"
                } else s"magnet:?xt=urn:btih:${_magnet()(pos)}"
                startActivity(Intent.createChooser(new Intent(Intent.ACTION_VIEW, Uri.parse(link)), getString(R.string.app_magnet)))
              }).setNeutralButton(R.string.app_copy, dialogClick { (d, _) =>
                val pos = d.asInstanceOf[AlertDialog].getListView.getCheckedItemPosition
                val item = _magnet()(pos)
                val link = if (item.contains(",")) s"https://yun.baidu.com/s/${item.split(",").head}" else s"magnet:?xt=urn:btih:${_magnet()(pos)}"
                clipboard(getString(R.string.app_magnet), link)
              }).create().show()
              menu.close(true)
            case _ if magnet < max => magnet += 1
              if (toast != null) toast.cancel()
              toast = Toast.makeText(getActivity, (0 until magnet).map(_ => "...").mkString, Toast.LENGTH_SHORT)
              toast.show()
            case _ =>
          }*/
        })

        /*
        * 显示网页内容的核心组件
        * 若加载失败则会不显示内容
        * 从而露出下方的image1（黑猫）
        * */
        val web: WebView = root.findViewById(R.id.web)
        val settings = web.getSettings
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
          settings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW)
        }
        settings.setJavaScriptEnabled(true)
        //设置好参数
        //静等query(url)获取数据后填充...?
        //不太像，不明白
        web.setWebViewClient(new WebViewClient {
          override def shouldOverrideUrlLoading(view: WebView, url: String): Boolean = {
            val uri = Uri.parse(url)
            startActivity(Intent.createChooser(new Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), uri.getScheme))
            true
          }
        })
        web.addJavascriptInterface(new JsFace(), "hacg")
        _web += web
      }
        /*评论页*/
      case 1 => container.inflate(R.layout.fragment_info_list).also { root =>
        val list: RecyclerView = root.findViewById(R.id.list1)
        list.setLayoutManager(new LinearLayoutManager(getActivity))
        list.setHasFixedSize(true)
        list.setAdapter(_adapter)
        list.loading() { () => query(_url, QUERY_COMMENT) }
        list.setBackgroundColor(ContextCompat.getColor(getActivity,R.color.background_material_dark))

        _progress2 += root.findViewById(R.id.swipe)
        _progress2.views.head.setOnRefreshListener(new OnRefreshListener {
          override def onRefresh(): Unit = {
            _url = null
            _adapter.clear()
            query(_article.link, QUERY_COMMENT)
          }
        })
        root.findViewById[View](R.id.button3).setOnClickListener(viewClick(_ => comment(null)))
      }
      case _ => throw new IllegalAccessException()
    }).also { root =>/*按钮随机颜色*/
      List(R.id.button1, R.id.button2, R.id.button3, R.id.button4, R.id.button5).map(root.findViewById[View]).foreach {

        case b: FloatingActionButton =>
          b.setColorNormal(randomColor())
          b.setColorPressed(randomColor())
          b.setColorRipple(randomColor())
        case _ =>
      }
      container.addView(root)
    }
  }

  /*分享按钮（大概）*/
  def share(url: String): Future[Unit] = {
    url.httpDownloadAsync(getContext) {
      case Some(file) =>
        val title = _article.title
        val intro = _article.content
        val url = _article.link
        startActivity(Intent.createChooser(
          new Intent(Intent.ACTION_SEND)
            .setType("image/*")
            .putExtra(Intent.EXTRA_TITLE, title)
            .putExtra(Intent.EXTRA_SUBJECT, title)
            .putExtra(Intent.EXTRA_TEXT, s"$title\n$intro $url")
            .putExtra(Intent.EXTRA_STREAM, Uri.fromFile(file))
            .putExtra(Intent.EXTRA_REFERRER, Uri.parse(url)),
          title))
      case _ =>
    }
  }

  //评论中的头像？
  class JsFace {
    @JavascriptInterface
    def play(name: String, url: String): Unit = {
      startActivity(Intent.createChooser(new Intent(Intent.ACTION_VIEW)
        .setDataAndType(Uri.parse(url), "video/mp4"), name))
    }

    @JavascriptInterface
    def save(url: String): Unit = {
      getActivity.runOnUiThread { () =>
        val uri = Uri.parse(url)
        val image = new ImageView(getActivity)
        image.setAdjustViewBounds(true)
        Picasso.`with`(getActivity).load(uri).placeholder(R.drawable.loading).into(image)
        val alert = new Builder(getActivity)
          .setView(image)
          .setNeutralButton(R.string.app_share, dialogClick { (_, _) => share(url) })
          .setPositiveButton(R.string.app_save,
            dialogClick { (_, _) =>
              val manager = HAcgApplication.instance.getSystemService(Context.DOWNLOAD_SERVICE).asInstanceOf[DownloadManager]
              val task = new Request(uri)
              task.allowScanningByMediaScanner()
              task.setNotificationVisibility(Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
              val ext = MimeTypeMap.getFileExtensionFromUrl(url)
              task.setMimeType(MimeTypeMap.getSingleton.getMimeTypeFromExtension(ext))
              manager.enqueue(task)
            })
          .setNegativeButton(R.string.app_cancel, null)
          .create()
        image.setOnClickListener(viewClick { _ => alert.dismiss() })
        alert.show()
      }
    }
  }

  def onBackPressed: Boolean = {
    getView.findViewById[View](R.id.container /*drawer*/) match {
      case menu: ViewPager if menu.getCurrentItem > 0 =>
        menu.setCurrentItem(0)
        true
      case _ =>
        //        _web.views.foreach(_.setVisibility(View.INVISIBLE))
        false
    }
  }

  /**
    * 弹出评论界面回复框
    * */
  def comment(c: Comment): Unit = {
    if (c == null) {
      commenting(c)
      return
    }
    val alert = new Builder(getActivity)
      .setTitle(c.user)
      .setMessage(c.content)
      .setPositiveButton(R.string.comment_review, dialogClick { (_, _) => commenting(c) })
      .setNegativeButton(R.string.app_cancel, null)
      .setNeutralButton(R.string.app_copy,
        dialogClick { (_, _) =>
          val clipboard = getActivity.getSystemService(Context.CLIPBOARD_SERVICE).asInstanceOf[ClipboardManager]
          val clip = ClipData.newPlainText(c.user, c.content)
          clipboard.setPrimaryClip(clip)
          Toast.makeText(getActivity, getActivity.getString(R.string.app_copied, c.content), Toast.LENGTH_SHORT).show()
        })
      .create()
    alert.setOnShowListener(new OnShowListener {
      override def onShow(dialog: DialogInterface): Unit = {
        def r(v: Seq[View]): Unit = v match {
          case Nil =>
          case a +: b => rr(a); r(b)
        }

        def rr(v: View): Unit = v match {
          case tv: TextView if !tv.isInstanceOf[Button] =>
            tv.setTextIsSelectable(true)
          case vg: ViewGroup =>
            r(for (i <- 0 until vg.getChildCount; sv = vg.getChildAt(i)) yield sv)
          case _ =>
        }

        rr(alert.getWindow.getDecorView)
      }
    })
    alert.show()
  }

  def commenting(c: Comment): Unit = {
    val input = LayoutInflater.from(getActivity).inflate(R.layout.comment_post, null)
    val author: EditText = input.findViewById(R.id.edit1)
    val email: EditText = input.findViewById(R.id.edit2)
    val content: EditText = input.findViewById(R.id.edit3)
    author.setText(_post(AUTHOR))
    email.setText(_post(EMAIL))
    content.setText(_post.getOrElse(COMMENT, ""))
    _post += ("comment_parent" -> (if (c != null) c.id.toString else "0"))

    def fill() = {
      _post += (AUTHOR -> author.getText.toString, EMAIL -> email.getText.toString, COMMENT -> content.getText.toString)
      val preference = PreferenceManager.getDefaultSharedPreferences(getActivity)
      preference.edit().putString(CONFIG_AUTHOR, _post(AUTHOR)).putString(CONFIG_EMAIL, _post(EMAIL)).apply()
    }

    new Builder(getActivity)
      .setTitle(if (c != null) getString(R.string.comment_review_to, c.user) else getString(R.string.comment_title))
      .setView(input)
      .setPositiveButton(R.string.comment_submit,
        dialogClick { (_, _) =>
          fill()
          if (COMMENTURL.isEmpty || List(AUTHOR, EMAIL, COMMENT).map(_post.getOrElse(_, null)).exists(_.isNullOrEmpty)) {
            Toast.makeText(getActivity, getString(R.string.comment_verify), Toast.LENGTH_SHORT).show()
          } else {
            _progress2 <= true
            async(this) { c =>
              val result = COMMENTURL.httpPost(_post.toMap).jsoup match {
                case Some(dom) =>
                  dom.select("#error-page").headOption match {
                    case Some(e) => (false, e.text())
                    case _ => (true, getString(R.string.comment_succeeded))
                  }
                case _ => (false, getString(R.string.comment_failed))
              }
              c.ui { _ =>
                _progress2 <= false
                if (result._1) {
                  _post(COMMENT) = ""
                  _url = null
                  _adapter.clear()
                  query(_article.link, QUERY_COMMENT)
                }
                Toast.makeText(getActivity, result._2, Toast.LENGTH_LONG).show()
              }
            }
          }
        })
      .setNegativeButton(R.string.app_cancel, null)
      .setOnDismissListener(dialogDismiss { _ => fill() })
      .create().show()
  }

  //说白了就是1、2、3...大概
  val QUERY_WEB = 1
  val QUERY_COMMENT: Int = QUERY_WEB << 1
  val QUERY_ALL: Int = QUERY_WEB | QUERY_COMMENT

  /*
  * 要在界面显示网页上的什么内容
  * 核心函数
  * */
  def query(url: String, op: Int): Unit = {
    if (_progress() || _progress2() || url.isNullOrEmpty) {
      return
    }
    _error <= false
    //根据传入的op判断要搜索的是内容还是评论
    val content = (op & QUERY_WEB) == QUERY_WEB
    val comment = (op & QUERY_COMMENT) == QUERY_COMMENT
    _progress <= content
    _progress2 <= true
    async(this) { c =>
      /*
      * 下载网页作为result
      * 定义entry变量选择要显示的内容
      * 并处理网页样式
      * 使用entry处理好的HTML内容
      * 根据查询的是content或comment进行处理
      * */
      val result = url.httpGet.jsoup { dom =>
        //对获取的数据进行加工处理
        //正版网站的推荐内容是在主content之外的，而盗版则是在entry-content内，因此需要添加
        val entry = dom.select(".entry-content")
        entry.select(".toggle-box").foreach(_.removeAttr("style"))
        entry.select("*[style*=display]").toList.filter(i => i.attr("style").matches("display: ?none;?")).foreach(_.remove())
        entry.select(".wp-polls-loading").remove()
        //删除所有脚本
        entry.select("script").toList.filter { e => !e.html().contains("renderVideo();") }.foreach(_.remove())
        entry.select("div[class*=slider]").toList.foreach(_.remove())
        entry.select("div:has(button)").toList.foreach(_.remove())
        entry.select("pre:has(a)").toList.foreach(_.remove())
        entry.select(".wp-polls").foreach(div => {
          val node = if (div.parent.attr("class") != "entry-content") div.parent else div
          val name = div.select("strong").headOption match {
            case Some(strong) => strong.text()
            case _ => "投票推荐"
          }
          node.after( s"""<a href="$url">$name</a>""")
          node.remove()
        })

        entry.select("*")/*.removeAttr("class")*/.removeAttr("style")
        entry.select("a[href=#]").foreach(i => i.attr("href", "javascript:void(0)"))
        entry.select("a[href$=#]").foreach(i => i.attr("href", i.attr("href").replaceAll("(.*?)#*", "$1")))
        entry.select("embed").unwrap()
        entry.append(dom.select("div.same_cat_posts").html())
        entry.select("a:has(img)").toList.foreach(_.wrap("<div class='relate_link_div'></div>"))
        //加载文章中的图片
        entry.select("img").foreach(i => {
          val src = i.attr("src")
          i.parents().find(_.tagName().equalsIgnoreCase("a")) match {
            case Some(a) =>
              a.attr("href") match {
                case href if src.equals(href) =>
                  a.attr("href", s"javascript:hacg.save('$src');")
                case href if href.isImg =>
                  a.attr("href", s"javascript:hacg.save('$src');").after( s"""<a href="javascript:hacg.save('$href');"><img data-original="$href" class="lazy" /></a>""")
                case _ =>
              }
            case _ => i.wrap( s"""<a href="javascript:hacg.save('$src');"></a>""")
          }
          i.attr("data-original", src)
            .addClass("lazy")
            .removeAttr("src")
            .removeAttr("width")
            .removeAttr("height")
        })


        (
          /*
          * 若为content则将其填充进assert下的template.html中
          * 在template.html中已经设置好了待显示HTML的样式
          * 若为comment则将对应列表存入全局变量（大概，未细看）
          * */
          if (content) using(scala.io.Source.fromInputStream(HAcgApplication.instance.getAssets.open("template.html"))) {
            reader => reader.mkString.replace("{{title}}", _article.title).replace("{{body}}", entry.html())
            //                  .replaceAll( """(?<!/|:)\b[a-zA-Z0-9]{40}\b""", """magnet:?xt=urn:btih:$0""")
            //                  .replaceAll( """(?<!['"=])magnet:\?xt=urn:btih:\b[a-zA-Z0-9]{40}\b""", """<a href="$0">$0</a>""")
            //                  .replaceAll( """\b([a-zA-Z0-9]{8})\b(\s)\b([a-zA-Z0-9]{4})\b""", """<a href="http://pan.baidu.com/s/$1">baidu:$1</a>$2$3""")
          } else null,
          /*然后是文章评论*/
          if (comment) dom.select("#comments .commentlist>li").map(e => new Comment(e)).toList else null,
          dom.select("#comments #comment-nav-below #comments-nav .next").headOption match {
            case Some(a) => a.attr("abs:href")
            case _ => null
          },
          dom.select("#commentform").select("textarea,input").map(o => (o.attr("name"), o.attr("value"))).toMap,
          dom.select("#commentform").attr("abs:action"),
          if (content) entry.text() else null
        )
      }

      /*
      * 获取足够的数据后
      * 开始填充UI
      * */
      c.ui { _ =>
        result match {
          case Some(data) =>
            if (content) {
              _magnet <=
                """\b([a-zA-Z0-9]{32}|[a-zA-Z0-9]{40})\b""".r.findAllIn(data._6).toList ++
                  """\b([a-zA-Z0-9]{8})\b\s+\b([a-zA-Z0-9]{4})\b""".r.findAllMatchIn(data._6).map(m => s"${m.group(1)},${m.group(2)}")
              _web <= (data._1, url)
            }
            if (comment) {
              _url = data._3
//              data._2.filter(_.moderation.isNonEmpty).foreach(println)
              _adapter.data --= _adapter.data.filter(_.isInstanceOf[String])
              _adapter ++= data._2
              _adapter += ((_adapter.data.isEmpty, _url.isNullOrEmpty) match {
                case (true, true) => getString(R.string.app_list_empty)
                case (false, true) => getString(R.string.app_list_complete)
                case (_, false) => getString(R.string.app_list_loading)
              })
            }
            COMMENT = data._4.find(o => o._1.matches(COMMENTPREFIX)) match {
              case Some(s) => s._1
              case _ => COMMENT
            }
            val filter = List(AUTHOR, EMAIL, COMMENT)
            _post ++= data._4.filter(o => !filter.contains(o._1))

            COMMENTURL = data._5
          case _ => _error <= (_web() == null)
        }
        _progress <= false
        _progress2 <= false
      }
    }
  }

  val datafmt = new SimpleDateFormat("yyyy-MM-dd hh:ss")

  /*
  * comment_item.xml
  * 评论列表
  * */
  class CommentHolder(view: View) extends RecyclerView.ViewHolder(view) {
    view.setBackgroundColor(R.color.accent_material_dark);
    val text1: TextView = view.findViewById(R.id.text1)
    val text2: TextView = view.findViewById(R.id.text2)
    val text3: TextView = view.findViewById(R.id.text3)
    val text4: TextView = view.findViewById(R.id.text4)
    val image: ImageView = view.findViewById(R.id.image1)
    //回复列表？
    val list: RecyclerView = view.findViewById(R.id.list1)
    val adapter = new CommentAdapter
    val context: Context = view.getContext
    //回复列表各项设置同样的样式
    list.setAdapter(adapter)
    list.setLayoutManager(new LinearLayoutManager(context))
    list.setHasFixedSize(true)
    view.setOnClickListener(_click)
  }

  /*
  * 大概是结尾‘没有更多信息’？
  * 不太像
  * */
  class MsgHolder(view: View) extends RecyclerView.ViewHolder(view) {
    val text1: TextView = view.findViewById(R.id.text1)
  }

  /*
  * 评论内容
  * 数据存储体
  * */
  class CommentAdapter extends DataAdapter[AnyRef, RecyclerView.ViewHolder] {

    override def onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int): Unit = {
      holder match {
        case holder: CommentHolder =>
          val item = data(position).asInstanceOf[Comment]
          /*
          * text1 用户名
          * text2 ？？？
          * text3 评论时间
          * text4 评论内容
          * */
          holder.itemView.setTag(item)
          holder.text1.setText(item.user)
          holder.text2.setText(item.content)
          holder.text3.setText(item.time.map(datafmt.format).orNull)
          holder.text3.setVisibility(if (item.time.isEmpty) View.GONE else View.VISIBLE)
          holder.text4.setText(item.moderation)
          holder.text4.setVisibility(if (item.moderation.isNullOrEmpty) View.GONE else View.VISIBLE)
          holder.adapter.clear ++= item.children

          /*用户头像*/
          if (item.face.isEmpty) {
            holder.image.setImageResource(R.mipmap.ic_launcher)
          } else {
            Picasso.`with`(holder.context).load(item.face).placeholder(R.mipmap.ic_launcher).into(holder.image)
          }
        case holder: MsgHolder =>
          holder.text1.setText(data(position).asInstanceOf[String])
      }

    }

    object CommentType extends Enumeration {
      val Comment = Value
      val Msg = Value
    }

    override def getItemViewType(position: Int): Int = data(position) match {
      case _: Comment => CommentType.Comment.id
      case _ => CommentType.Msg.id
    }

    override def onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder = CommentType(viewType) match {
      case CommentType.Comment => new CommentHolder(parent.inflate(R.layout.comment_item))
      case _ => new MsgHolder(parent.inflate(R.layout.list_msg_item))
    }

  }

}
