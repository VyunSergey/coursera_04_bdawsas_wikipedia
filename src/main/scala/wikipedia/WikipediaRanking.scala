package wikipedia

import org.apache.spark.rdd.RDD
import org.apache.spark.{SparkConf, SparkContext}

case class WikipediaArticle(title: String, text: String) {
  /**
    * @return Whether the text of this article mentions `lang` or not
    * @param lang Language to look for (e.g. "Scala")
    */
  def mentionsLanguage(lang: String): Boolean = text.split(' ').contains(lang)
}

object WikipediaRanking {

  val langs = List(
    "JavaScript", "Java", "PHP", "Python", "C#", "C++", "Ruby", "CSS",
    "Objective-C", "Perl", "Scala", "Haskell", "MATLAB", "Clojure", "Groovy")

  val conf: SparkConf = new SparkConf().setMaster("local[*]").setAppName("Wikipedia-Ranking")
  val sc: SparkContext = new SparkContext(conf)

  // Hint: use a combination of `sc.textFile`, `WikipediaData.filePath` and `WikipediaData.parse`
  val wikiRdd: RDD[WikipediaArticle] = sc.textFile(WikipediaData.filePath).map(line => WikipediaData.parse(line))

  /** Returns the number of articles on which the language `lang` occurs.
   *  Hint1: consider using method `aggregate` on RDD[T].
   *  Hint2: consider using method `mentionsLanguage` on `WikipediaArticle`
   */
  def occurrencesOfLang(lang: String, rdd: RDD[WikipediaArticle]): Int = rdd.aggregate(0)(
    (num: Int, wiki: WikipediaArticle) => num + (if (wiki.mentionsLanguage(lang)) 1 else 0),
    (num1: Int, num2: Int) => num1 + num2
  )

  /* (1) Use `occurrencesOfLang` to compute the ranking of the languages
   *     (`val langList`) by determining the number of Wikipedia articles that
   *     mention each language at least once. Don't forget to sort the
   *     languages by their occurrence, in decreasing order!
   *
   *   Note: this operation is long-running. It can potentially run for
   *   several seconds.
   */
  def rankLangs(langList: List[String], rdd: RDD[WikipediaArticle]): List[(String, Int)] = langList.map {
    lang => (lang, occurrencesOfLang(lang, rdd))
  }.sortBy(_._2).reverse

  /* Compute an inverted index of the set of articles, mapping each language
   * to the Wikipedia pages in which it occurs.
   */
  def makeIndex(langList: List[String], rdd: RDD[WikipediaArticle]): RDD[(String, Iterable[WikipediaArticle])] = rdd.flatMap {
    wiki => for {
      lang <- langList if wiki.mentionsLanguage(lang)
    } yield (lang, wiki)
  }.groupByKey

  /* (2) Compute the language ranking again, but now using the inverted index. Can you notice
   *     a performance improvement?
   *
   *   Note: this operation is long-running. It can potentially run for
   *   several seconds.
   */
  def rankLangsUsingIndex(index: RDD[(String, Iterable[WikipediaArticle])]): List[(String, Int)] = index
    .mapValues(_.size).collect().toList.sortBy(_._2).reverse

  /* (3) Use `reduceByKey` so that the computation of the index and the ranking are combined.
   *     Can you notice an improvement in performance compared to measuring *both* the computation of the index
   *     and the computation of the ranking? If so, can you think of a reason?
   *
   *   Note: this operation is long-running. It can potentially run for
   *   several seconds.
   */
  def rankLangsReduceByKey(langs: List[String], rdd: RDD[WikipediaArticle]): List[(String, Int)] = rdd.flatMap {
    wiki => for {
      lang <- langs if wiki.mentionsLanguage(lang)
    } yield (lang, 1)
  }.reduceByKey(_ + _).collect().toList.sortBy(_._2).reverse

  def main(args: Array[String]) {

    /* Languages ranked according to (1) */
    val langListRanked: List[(String, Int)] = timed("Part 1: naive ranking",
      rankLangs(langs, wikiRdd))
    langListRanked.foreach(println)

    /* An inverted index mapping languages to wikipedia pages on which they appear */
    def index: RDD[(String, Iterable[WikipediaArticle])] = makeIndex(langs, wikiRdd)

    /* Languages ranked according to (2), using the inverted index */
    val langListRanked2: List[(String, Int)] = timed("Part 2: ranking using inverted index",
      rankLangsUsingIndex(index))
    langListRanked2.foreach(println)

    /* Languages ranked according to (3) */
    val langListRanked3: List[(String, Int)] = timed("Part 3: ranking using reduceByKey",
      rankLangsReduceByKey(langs, wikiRdd))
    langListRanked3.foreach(println)

    /* Output the speed of each ranking */
    println(timing)
    sc.stop()
  }

  val timing = new StringBuffer
  def timed[T](label: String, code: => T): T = {
    val start = System.currentTimeMillis()
    val result = code
    val stop = System.currentTimeMillis()
    timing.append(s"Processing $label took ${stop - start} ms.\n")
    result
  }
}
