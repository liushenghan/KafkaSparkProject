import org.apache.spark.SparkConf
import org.apache.spark.SparkContext
import org.apache.spark.SparkContext._
import org.apache.spark.streaming.{Seconds, StreamingContext}
import org.apache.spark.streaming.StreamingContext._
import org.apache.spark.streaming.kafka.KafkaUtils
import org.jets3t.samples.S3PostFormBuilder
import org.json4s._
import org.json4s.jackson.JsonMethods._
object KafkaEventConsumer {
  implicit val formats = DefaultFormats
  case class ReceiveLine(uid:String, event_time:String, os_type:String, click_count:Integer)
  def main(args: Array[String]) {
    val Array(zkQuorum, group, topics, numThreads) = args
    //val Array(zkQuorum, group, topics, numThreads) = {"132.96.179.4:2181\,132.96.179.5:2181\,132.96.179.6:2181","jd-group","spark_test_user_events","1"}
    println(topics)
    val topicMap = topics.split(",").map((_,numThreads.toInt)).toMap
    topicMap.foreach(println)
    println(zkQuorum)
    val sparkConf = new SparkConf().setMaster("local[2]").setAppName("LogStash")
    sparkConf.set("spark.testing.memory", "2147480000")//后面的值大于512m即可
    val sc  = new SparkContext(sparkConf)
    val ssc = new StreamingContext(sc, Seconds(10))
    val kafkaStream = KafkaUtils.createStream(ssc, zkQuorum, group, topicMap)
    //val kafkaStream = KafkaUtils.createStream(ssc, "132.96.179.4:2181", "jd-group", {"spark_test_user_events":1})
    println("kafkaStream.print()")
    kafkaStream.print()
    /*输出
    (null,{"uid":"a95f22eabc4fd4b580c011a3161a9d9d","event_time":"1467424630329","os_type":"Android","click_count":6})
    (null,{"uid":"4A4D769EB9679C054DE81B973ED5D768","event_time":"1467424632330","os_type":"Android","click_count":7})
    (null,{"uid":"8dfeb5aaafc027d89349ac9a20b3930f","event_time":"1467424634331","os_type":"Android","click_count":9})
    */
    //取每个tuple的第二个元素，结果组成一个新的Dstream,相当于val lines = kafkaStream.map(t=>t._2)
    val lines = kafkaStream.map(_._2)
    lines.print()
    /*输出
    {"uid":"a95f22eabc4fd4b580c011a3161a9d9d","event_time":"1467424630329","os_type":"Android","click_count":6}
    {"uid":"4A4D769EB9679C054DE81B973ED5D768","event_time":"1467424632330","os_type":"Android","click_count":7}
    {"uid":"8dfeb5aaafc027d89349ac9a20b3930f","event_time":"1467424634331","os_type":"Android","click_count":9}
    */

    // val userClicks = lines.map(x => (x.getString("uid"), x.getInt("click_count")))
    println("receive lines")
    //println(lines.toString)
    //lines.map(line=>{println(line)})


    val linesMap = lines.map(line => {
      val json = parse(line)
      println(json)
      /*输出
      JObject(List((uid,JString(6b67c8c700427dee7552f81f3228c927)), (event_time,JString(1467425329634)), (os_type,JString(Android)), (click_count,JInt(2))))
      JObject(List((uid,JString(a95f22eabc4fd4b580c011a3161a9d9d)), (event_time,JString(1467425331731)), (os_type,JString(Android)), (click_count,JInt(7))))
      JObject(List((uid,JString(4A4D769EB9679C054DE81B973ED5D768)), (event_time,JString(1467425333733)), (os_type,JString(Android)), (click_count,JInt(0))))
     */
      val listA =json.extract[ReceiveLine]
      println(listA)
      /*list的内容
      ReceiveLine(a95f22eabc4fd4b580c011a3161a9d9d,1467433938190,Android,3)
      ReceiveLine(4A4D769EB9679C054DE81B973ED5D768,1467433940190,Android,3)
      */
      (listA.uid,listA.click_count)
    }).reduceByKey(_+_)

    linesMap.print()
    /*输出
    (c8ee90aade1671a21336c721512b817a,16)
    (068b746ed4620d25e26055a9f804385f,24)
    (97edfc08311c70143401745a03a50706,16)
    (d7f141563005d1b5d0d3dd30138f3f62,27)
    (a95f22eabc4fd4b580c011a3161a9d9d,32)
    (4A4D769EB9679C054DE81B973ED5D768,16)
    (8dfeb5aaafc027d89349ac9a20b3930f,19)
    (6b67c8c700427dee7552f81f3228c927,22)
    (011BBF43B89BFBF266C865DF0397AA71,20)
    (f2a8474bf7bd94f0aabbd4cdd2c06dcf,20)
    */

    ssc.start()
    ssc.awaitTermination()
  }
}