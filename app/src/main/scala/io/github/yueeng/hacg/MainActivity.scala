package io.github.yueeng.hacg

import java.util.concurrent.Future

import android.animation.ObjectAnimator
import android.app.SearchManager
import android.content.{Context, _}
import android.graphics.{PixelFormat, Point}
import android.net.Uri
import android.os.{Bundle, Parcelable}
import android.provider.SearchRecentSuggestions
import android.support.design.widget.TabLayout
import android.support.v4.app._
import android.support.v4.view.{PagerAdapter, ViewPager}
import android.support.v4.widget.SwipeRefreshLayout
import android.support.v4.widget.SwipeRefreshLayout.OnRefreshListener
import android.support.v7.app.AlertDialog.Builder
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.{RecyclerView, SearchView, StaggeredGridLayoutManager}
import android.text.method.LinkMovementMethod
import android.util.Log
import android.view.ViewGroup.LayoutParams
import android.view.animation.DecelerateInterpolator
import android.view.{Menu, View, WindowManager, _}
import android.widget.CompoundButton.OnCheckedChangeListener
import android.widget.{TextView, _}
import com.squareup.picasso.Picasso
import io.github.yueeng.hacg.Common._
import io.github.yueeng.hacg.ViewBinder.{ErrorBinder, ViewBinder}

import scala.collection.JavaConversions._
import scala.collection.mutable.ListBuffer
import scala.ref.WeakReference

class MainActivity extends AppCompatActivity {

  lazy val pager: ViewPager = findViewById(R.id.container)
  var mWindowManager:WindowManager = null
  var tv:TextView = null
  var lp:(WindowManager.LayoutParams) = null

  protected override def onCreate(state: Bundle) {
    super.onCreate(state)
    /*
    * 设置布局
    * */
    setContentView(R.layout.activity_main)
    Log.e("MainActivity","onCreate");

    mWindowManager = getSystemService(Context.WINDOW_SERVICE).asInstanceOf[WindowManager]
    lp = new WindowManager.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.TYPE_APPLICATION,
      WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.TRANSLUCENT)
    lp.gravity = Gravity.BOTTOM // 可以自定义显示的位置
    lp.y = 10 // 距离底部的距离是10像素 如果是 top 就是距离top是10像素
    tv = new TextView(MainActivity.this)
    tv.setBackgroundColor(0x99000000)

    setSupportActionBar(findViewById(R.id.toolbar))
    getSupportActionBar.setLogo(R.mipmap.ic_launcher)
    /*TabLayout:横向滑动布局*/
    val tabs: TabLayout = findViewById(R.id.tab)
    /*
    * 横向滑动内容
    * ViewPager 如其名所述，是负责翻页的一个 View。
    * 准确说是一个 ViewGroup，包含多个 View 页，在手指横向滑动屏幕时，其负责对 View 进行切换。
    * 为了生成这些 View 页，需要提供一个 PagerAdapter 来进行和数据绑定以及生成最终的 View 页
    * （摘自:https://www.cnblogs.com/lianghui66/p/3607091.html）
    * */
    pager.setAdapter(new ArticleFragmentAdapter(getSupportFragmentManager))
    tabs.setupWithViewPager(pager)

    if (state == null) {
      checkVersion()
    }

    new Thread(new SearchSite()).start()

  }

  /*
  * 若连续两次点击返回，则退出程序
  * */
  private var last = 0L
  override def onBackPressed(): Unit = {
    if (System.currentTimeMillis() - last > 1500) {
      last = System.currentTimeMillis()
      toast(R.string.app_exit_confirm)
      return
    }
    if (NightMode.isNight)
      mWindowManager.removeViewImmediate(tv);
    mWindowManager = null;
    tv = null;
    finish()
    //    Snackbar.make(findViewById(R.id.coordinator), R.string.app_exit_confirm, Snackbar.LENGTH_SHORT)
    //      .setAction(R.string.app_exit, viewClick { _ => ActivityCompat.finishAfterTransition(MainActivity.this) })
    //      .show()
  }

  /*
  * check new version
  * */
  def checkVersion(toast: Boolean = false): Future[Unit] = {
    async(this) { c =>
      val result = s"${HAcg.RELEASE}/latest".httpGet.jsoup { dom =>
        (
          dom.select(".css-truncate-target").text(),
          dom.select(".markdown-body").html().trim,
          dom.select(".release a[href$=.apk]").headOption match {
            case Some(a) => a.attr("abs:href")
            case _ => null
          }
        )
      } match {
        case Some((v: String, t: String, u: String)) if Common.versionBefore(Common.version(MainActivity.this), v) => Option(v, t, u)
        case _ => None
      }
      c.ui { _ =>
        result match {
          case Some((v: String, t: String, u: String)) =>
            new Builder(MainActivity.this)
              .setTitle(getString(R.string.app_update_new, Common.version(MainActivity.this), v))
              .setMessage(t.html)
              .setPositiveButton(R.string.app_update, dialogClick { (_, _) => openWeb(MainActivity.this, u) })
              .setNeutralButton(R.string.app_publish, dialogClick { (_, _) => openWeb(MainActivity.this, HAcg.RELEASE) })
              .setNegativeButton(R.string.app_cancel, null)
              .create().show()
          case _ =>
            if (toast) {
              Toast.makeText(MainActivity.this, getString(R.string.app_update_none, Common.version(MainActivity.this)), Toast.LENGTH_SHORT).show()
            }
        }
      }
    }
  }

  def reload(): Unit = {
    pager.setAdapter(new ArticleFragmentAdapter(getSupportFragmentManager))
  }

  /*
  * FragmentPagerAdapter 继承自 PagerAdapter。
  * 相比通用的 PagerAdapter，该类更专注于每一页均为 Fragment 的情况。
  * 该类内的每一个生成的 Fragment 都将保存在内存之中，因此适用于那些相对静态的页，数量也比较少的那种；
  * 如果需要处理有很多页，并且数据动态性较大、占用内存较多的情况，应该使用FragmentStatePagerAdapter。
  *
  * 该 PagerAdapter 的实现将只保留当前页面，当页面离开视线后，就会被消除，释放其资源；而在页面需要显示时，生成新的页面
  *
  * （摘自:https://www.cnblogs.com/lianghui66/p/3607091.html）
  * */
  class ArticleFragmentAdapter(fm: FragmentManager) extends FragmentStatePagerAdapter(fm) {
    private val data = ListBuffer(HAcg.category: _*)

    override def getItem(position: Int): Fragment = {
      Log.e("ArticleFragmentAdapter url",""+data(position)._1);
      return new ArticleFragment().arguments(new Bundle().string("url", data(position)._1))
    }


    override def getCount: Int = data.size

    override def getPageTitle(position: Int): CharSequence = data(position)._2

    /*
    * 有的建议不用 FragmentPagerAdapter，而改用 FragmentStatePagerAdapter，
    * 并且重载 getItemPosition() 并返回 POSITION_NONE，以触发销毁对象以及重建对象。
    * 从上面的分析中看，后者给出的建议确实可以达到调用 notifyDataSetChanged() 后，Fragment 被以新的参数重新建立的效果
    * */
    override def getItemPosition(`object`: scala.Any): Int = PagerAdapter.POSITION_NONE

    var current: WeakReference[Fragment] = _

    override def setPrimaryItem(container: ViewGroup, position: Int, `object`: scala.Any): Unit = {
      super.setPrimaryItem(container, position, `object`)
      current = WeakReference(`object`.asInstanceOf[Fragment])
    }
  }

  /*
  * 上方菜单栏搜索功能
  * */
  override def onCreateOptionsMenu(menu: Menu): Boolean = {
    getMenuInflater.inflate(R.menu.menu_main, menu)
    val search = menu.findItem(R.id.search).getActionView.asInstanceOf[SearchView]
    val manager = getSystemService(Context.SEARCH_SERVICE).asInstanceOf[SearchManager]
    val info = manager.getSearchableInfo(new ComponentName(this, classOf[ListActivity]))
    search.setSearchableInfo(info)

    val night:Switch = menu.findItem(R.id.switchNightView).getActionView.findViewById(R.id.switchNight)
    night.setOnCheckedChangeListener(new OnCheckedChangeListener {
      override def onCheckedChanged(buttonView: CompoundButton, isChecked: Boolean): Unit = {
        NightMode.isNight = isChecked
        if (isChecked)
          mWindowManager.addView(MainActivity.this.tv, MainActivity.this.lp)
        else
          mWindowManager.removeViewImmediate(MainActivity.this.tv)
      }
    })
    super.onCreateOptionsMenu(menu)
  }

  /*
  * 点击菜单栏后对应的操作
  * */
  override def onOptionsItemSelected(item: MenuItem): Boolean = {
    item.getItemId match {
        //search
      case R.id.search_clear =>
        val suggestions = new SearchRecentSuggestions(this, SearchHistoryProvider.AUTHORITY, SearchHistoryProvider.MODE)
        suggestions.clearHistory()
        true
      case R.id.config => HAcg.update(this)(() => reload());true
      case R.id.config_fade => HAcg.update_fade(this)(() => reload());true
      case R.id.settings => HAcg.setHost(this, _ => reload());true
      case R.id.philosophy => startActivity(new Intent(this, classOf[WebActivity])); true
      case R.id.searchSite => clipboard(null,SearchSite.getUrl(SearchSite.SEARCH_BAIDU));true
      case R.id.searchLiulishe => clipboard(null,SearchSite.getUrl(SearchSite.SEARCH_LIULISHE));true
      case R.id.about =>
        new Builder(this)
          .setTitle(s"${getString(R.string.app_name)} ${Common.version(this)}")
          .setItems(Array[CharSequence](getString(R.string.app_name)),
            dialogClick { (_, _) => openWeb(MainActivity.this, HAcg.wordpress) })
          .setPositiveButton(R.string.app_publish,
            dialogClick { (_, _) => openWeb(MainActivity.this, HAcg.RELEASE) })
          .setNeutralButton(R.string.app_update_check,
            dialogClick { (_, _) => checkVersion(true) })
          .setNegativeButton(R.string.app_cancel, null)
          .create().show()
        true
      case _ => super.onOptionsItemSelected(item)
    }
  }
}

//搜索界面
class ListActivity extends BaseSlideCloseActivity {

  override def onCreate(savedInstanceState: Bundle): Unit = {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_list)
    setSupportActionBar(findViewById(R.id.toolbar))
    getSupportActionBar.setLogo(R.mipmap.ic_launcher)
    getSupportActionBar.setDisplayHomeAsUpEnabled(true)

    val (url, name) = getIntent match {
      case i if i.hasExtra("url") => (i.getStringExtra("url"), i.getStringExtra("name"))
      case i if i.hasExtra(SearchManager.QUERY) =>
        val key = i.getStringExtra(SearchManager.QUERY)
        val suggestions = new SearchRecentSuggestions(this, SearchHistoryProvider.AUTHORITY, SearchHistoryProvider.MODE)
        suggestions.saveRecentQuery(key, null)
        //进行搜索查询
        ( s"""${HAcg.wordpress}/?s=${Uri.encode(key)}&submit=%E6%90%9C%E7%B4%A2""", key)
      case _ => null
    }
    if (url == null) {
      finish()
      return
    }
    Log.e("MainActivity URL SEARCH",url);
    setTitle(name)
    val transaction = getSupportFragmentManager.beginTransaction()

    val fragment = getSupportFragmentManager.findFragmentById(R.id.container) match {
      case fragment: ArticleFragment => fragment
      case _ => new ArticleFragment().arguments(new Bundle().string("url", url))
    }

    transaction.replace(R.id.container, fragment)

    transaction.commit()
  }

  override def onOptionsItemSelected(item: MenuItem): Boolean = {
    item.getItemId match {
      case android.R.id.home => onBackPressed(); true
      case _ => super.onOptionsItemSelected(item)
    }
  }
}

/*
* SearchRecentsuggestionsProvider这个超类能够被用来创建一个简单的搜索建议提供器，它创建基于最近的搜索或最近的view。
* 实现和测试查询搜索，SearchManager。
* 创建一个内容提供器在你的应用程序通过扩展SearchRecentSugestionsprovider
* */
class SearchHistoryProvider extends SearchRecentSuggestionsProvider() {
  setupSuggestions(SearchHistoryProvider.AUTHORITY, SearchHistoryProvider.MODE)
}

/*搜索历史*/
object SearchHistoryProvider {
  val AUTHORITY = s"${getClass.getPackage.getName}.SuggestionProvider"
  val MODE: Int = SearchRecentSuggestionsProvider.DATABASE_MODE_QUERIES
}

/*
* 左右滑动加载的Fragment
* 或是用来显示搜索结果
* */
class ArticleFragment extends Fragment {
  var busy = new ViewBinder[Boolean, SwipeRefreshLayout](false)((view, value) => view.post(() => view.setRefreshing(value)))
  lazy val adapter = new ArticleAdapter()
  var url: String = _
  val error = new ErrorBinder(false) {
    override def retry(): Unit = query(defurl, retry = true)
  }

  override def onCreate(saved: Bundle): Unit = {
    super.onCreate(saved)
    setRetainInstance(true)
    /*
    * 若已经加载过，则不必重新加载
    * （除非下拉刷新）
    * */
    if (saved != null) {
      val data = saved.getParcelableArray("data")
      if (data != null && data.nonEmpty) {
        adapter ++= data.map(_.asInstanceOf[Article])
        return
      }
      error <= saved.getBoolean("error", false)
    }
    Log.e("MainActivity ArticleFragment new",defurl);
    query(defurl)
  }

  /*
  * 获取该窗口的URL以便加载文章列表
  * */
  def defurl: String = getArguments.getString("url") match {
    case uri if uri.startsWith("/") => s"${HAcg.web}$uri"
    case uri => uri
  }

  /*
  * 离开时保存该窗口内容
  * */
  override def onSaveInstanceState(out: Bundle): Unit = {
    out.putParcelableArray("data", adapter.data.toArray)
    out.putBoolean("error", error())
  }

  /*
  * 刷新重新加载
  * */
  def reload(): Unit = {
    adapter.clear()
    query(defurl)
  }

  override def onCreateView(inflater: LayoutInflater, container: ViewGroup, savedInstanceState: Bundle): View = {
    val root = inflater.inflate(R.layout.fragment_list, container, false)
    error += root.findViewById(R.id.image1)
    busy += root.findViewById(R.id.swipe)
    busy.views.head.setOnRefreshListener(new OnRefreshListener {
      override def onRefresh(): Unit = {
        adapter.clear()
        query(defurl)
      }
    })
    val recycler: RecyclerView = root.findViewById(R.id.recycler)
    val layout = new StaggeredGridLayoutManager(getResources.getInteger(R.integer.main_list_column), StaggeredGridLayoutManager.VERTICAL)
    recycler.setLayoutManager(layout)
    recycler.setHasFixedSize(true)
    recycler.setAdapter(adapter)
    recycler.loading() { () => query(url) }

    root
  }

  /*
  * 搜索网站页面
  * */
  def query(uri: String, retry: Boolean = false): Unit = {
    if (busy() || uri.isNullOrEmpty) return
    busy <= true
    error <= false
    async(this) { c =>
      val result = uri.httpGet.jsoup { dom =>
        (dom.select("article").map(o => new Article(o)).toList,
          dom.select("#wp_page_numbers a").lastOption match {
            case Some(n) if ">" == n.text() => n.attr("abs:href")
            case _ => dom.select("#nav-below .nav-previous a").headOption match {
              case Some(p) => p.attr("abs:href")
              case _ => null
            }
          })
      }

      c.ui { _ =>
        result match {
          case Some(r) =>
            url = r._2
            /*
            * 根据URL是否为空和实际内容是否加载进行相应的显示
            * */
            adapter.data --= adapter.data.filter(_.link.isNullOrEmpty)
            adapter ++= r._1
            val msg = (adapter.data.isEmpty, url.isNullOrEmpty) match {
              case (true, true) => R.string.app_list_empty
              case (false, true) => R.string.app_list_complete
              case (_, false) => R.string.app_list_loading
            }
            adapter += new Article(getString(msg))
          case _ =>
            error <= (adapter.size == 0)
            if (error()) if (retry) getActivity.openOptionsMenu() else toast(R.string.app_network_retry)
        }
        busy <= false
      }
    }
  }

  /*
  * 点击目标后进入该文章
  * 加载文章详情
  * */
  private val click = viewClick {
    _.getTag match {
      case h: ArticleHolder =>
        startActivity(new Intent(getActivity, classOf[InfoActivity])
          .putExtra("article", h.article.asInstanceOf[Parcelable]))
      case _ =>
    }
  }

  /*
  * holder
  * 得到每篇文章的概要所需的组件
  * */
  class ArticleHolder(val view: View) extends RecyclerView.ViewHolder(view) {
    view.setOnClickListener(click)
    val context: Context = view.getContext
    val text1: TextView = view.findViewById(R.id.text1)
    val text2: TextView = view.findViewById(R.id.text2)
    val text3: TextView = view.findViewById(R.id.text3)
    val image1: ImageView = view.findViewById(R.id.image1)
    var article: Article = _
    view.setTag(this)
    text3.setMovementMethod(LinkMovementMethod.getInstance())
  }

  class MsgHolder(view: View) extends RecyclerView.ViewHolder(view) {
    val text1: TextView = view.findViewById(R.id.text1)
  }

  object ArticleType extends Enumeration {
    val Article = Value
    val Msg = Value
  }

  /*
  * 每篇文章的概要
  * 信息存储体
  * */
  class ArticleAdapter extends DataAdapter[Article, RecyclerView.ViewHolder] {
    override def onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int): Unit = {
      holder match {
          //加载文章，获取文章信息
        case holder: ArticleHolder =>
          val item = data(position)
          holder.article = item
          holder.text1.setText(item.title)
          holder.text1.setVisibility(if (item.title.isNonEmpty) View.VISIBLE else View.GONE)
          holder.text1.setTextColor(Common.randomColor())
          holder.text2.setText(item.content)
          holder.text2.setVisibility(if (item.content.isNonEmpty) View.VISIBLE else View.GONE)

          val span = SpanUtil.spannable(item.expend)(t2str = _.name, call = { tag => startActivity(new Intent(getActivity, classOf[ListActivity]).putExtra("url", tag.url).putExtra("name", tag.name)) })
          holder.text3.setText(span)
          holder.text3.setVisibility(if (item.tags.nonEmpty) View.VISIBLE else View.GONE)

          Picasso.`with`(holder.context).load(item.img).placeholder(R.drawable.loading).error(R.drawable.placeholder).into(holder.image1)
          //单纯的信息
        case holder: MsgHolder =>
          holder.text1.setText(data(position).title)
      }
      //界面之外的处理？
      if (position > last) {
        last = position
        val anim = ObjectAnimator.ofFloat(holder.itemView, "translationY", from, 0)
          .setDuration(1000)
        anim.setInterpolator(interpolator)
        anim.start()
      }
    }

    var last: Int = -1
    val interpolator = new DecelerateInterpolator(3)
    val from: Int = getActivity.getWindowManager.getDefaultDisplay match {
      case d: Display =>
        val p = new Point()
        d.getSize(p)
        Math.max(p.x, p.y) / 4
      case _ => 300;
    }

    override def getItemViewType(position: Int): Int = data(position) match {
      case a if a.link.isNonEmpty => ArticleType.Article.id
      case _ => ArticleType.Msg.id
    }

    override def onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder = ArticleType(viewType) match {
      case ArticleType.Article => new ArticleHolder(LayoutInflater.from(parent.getContext).inflate(R.layout.article_item, parent, false))
      case _ => new MsgHolder(parent.inflate(R.layout.list_msg_item))
    }
  }

}
