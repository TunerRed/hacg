package io.github.yueeng.hacg

import java.util.Date

import android.content.ContentResolver
import android.os.{Parcel, Parcelable}
import io.github.yueeng.hacg.Common._
import org.jsoup.nodes.Element

import scala.collection.JavaConversions._
import scala.language.implicitConversions
import scala.util.matching.Regex

case class Tag(name: String, url: String) extends Parcelable {
  def this(e: Element) = {
    this(e.text(), e.attr("abs:href"))
  }

  override def describeContents() = 0

  override def writeToParcel(dest: Parcel, flags: Int) {
    dest.writeString(name)
    dest.writeString(url)
  }
}

object Tag {

  val CREATOR: Parcelable.Creator[Tag] = new Parcelable.Creator[Tag] {
    override def createFromParcel(source: Parcel): Tag = new Tag(
      source.readString(),
      source.readString()
    )

    override def newArray(size: Int): Array[Tag] = new Array[Tag](size)
  }
}

case class Comment(id: Int, content: String, user: String, face: String, moderation: String, time: Option[Date], children: List[Comment]) extends Parcelable {
  def this(e: Element) = {
    this(
      Comment.ID.findPrefixMatchOf(e.select(">article").attr("id")) match {
        case Some(i) => i.group(1).toInt
        case _ => 0
      },
      e.select(">article .comment-content").text(),
      e.select(">article .fn").text(),
      e.select(">article .avatar").attr("abs:src"),
      e.select(">article footer>.comment-awaiting-moderation").text(),
      e.select(">article time").attr("datetime"),
      e.select(">.children>li").map(e => new Comment(e)).toList
    )
  }

  override def describeContents() = 0

  override def writeToParcel(dest: Parcel, flags: Int) {
    dest.writeInt(id)
    dest.writeString(content)
    dest.writeString(user)
    dest.writeString(face)
    dest.writeString(moderation)
    dest.writeLong(if (time.nonEmpty) time.get.getTime else 0)
    dest.writeParcelableArray(children.toArray, flags)
  }
}

object Comment {
  val ID: Regex = """comment-(\d+)""".r
  val CREATOR: Parcelable.Creator[Comment] = new Parcelable.Creator[Comment] {
    override def createFromParcel(source: Parcel): Comment = new Comment(
      source.readInt(),
      source.readString(),
      source.readString(),
      source.readString(),
      source.readString(),
      source.readLong() match {
        case 0 => None
        case l => Option(new Date(l))
      },
      source.readParcelableArray(classOf[Comment].getClassLoader).map(_.asInstanceOf[Comment]).toList
    )

    override def newArray(size: Int): Array[Comment] = new Array[Comment](size)
  }
}

case class Article(title: String,
                   link: String,
                   image: String,
                   content: String,
                   time: Option[Date],
                   comments: Int,
                   author: Option[Tag],
                   category: Option[Tag],
                   tags: List[Tag]) extends Parcelable {
  def this(msg: String) = this(msg, null, null, null, None, 0, None, None, Nil)

  private val defimg = s"${ContentResolver.SCHEME_ANDROID_RESOURCE}://${getClass.getPackage.getName}/drawable/placeholder"

  def img: String = if (image.isNullOrEmpty) defimg else image

  lazy val expend: List[Tag] = tags ++ category ++ author

  def this(e: Element) = {
    this(e.select("header .entry-title a").text().trim,
      e.select("header .entry-title a").attr("abs:href"),
      e.select(".entry-content img") match {
        case img if img.hasClass("avatar") => ""
        case img => img.attr("abs:src")
      },
      e.select(".entry-content p,.entry-summary p").text().trim,
      e.select("time").attr("datetime"),
      e.select("header .comments-link").text().trim match {
        case str if str.matches( """^$\d+""") => str.toInt
        case _ => 0
      },
      e.select("header .author a").take(1).map(e => new Tag(e)).headOption,
      e.select("footer .cat-links a").take(1).map(e => new Tag(e)).headOption,
      e.select("footer .tag-links a").map(o => new Tag(o)).toList)
  }

  override def describeContents() = 0

  override def writeToParcel(dest: Parcel, flags: Int) {
    dest.writeString(title)
    dest.writeString(link)
    dest.writeString(image)
    dest.writeString(content)
    dest.writeLong(if (time.nonEmpty) time.get.getTime else 0)
    dest.writeInt(comments)
    dest.writeParcelable(author.orNull, flags)
    dest.writeParcelable(category.orNull, flags)
    dest.writeParcelableArray(tags.toArray, flags)
  }
}

object Article {

  /*
  * Parcelable是Android为我们提供的序列化的接口,
  * Parcelable相对于Serializable的使用相对复杂一些,但Parcelable的效率相对Serializable也高很多,这一直是Google工程师引以为傲的
  * (摘自https://blog.csdn.net/justin_1107/article/details/72903006)
  * */
  val CREATOR: Parcelable.Creator[Article] = new Parcelable.Creator[Article] {
    override def createFromParcel(source: Parcel): Article = new Article(
      source.readString(),
      source.readString(),
      source.readString(),
      source.readString(),
      source.readLong() match {
        case 0 => None
        case l => Option(new Date(l))
      },
      source.readInt(),
      Option(source.readParcelable(classOf[Tag].getClassLoader).asInstanceOf[Tag]),
      Option(source.readParcelable(classOf[Tag].getClassLoader).asInstanceOf[Tag]),
      source.readParcelableArray(classOf[Tag].getClassLoader).map(_.asInstanceOf[Tag]).toList
    )

    override def newArray(size: Int): Array[Article] = new Array[Article](size)
  }
}
