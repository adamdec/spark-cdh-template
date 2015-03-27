package com.cloudera.ps.examples.spark

import com.databricks.spark.avro.AvroSaver
import org.apache.avro.generic.GenericRecord
import org.apache.avro.mapred.AvroInputFormat
import org.apache.spark.{ SparkConf, SparkContext }

object Main extends App {

  val yarn = true

  def getJar(klass: Class[_]): String = {
    val codeSource = klass.getProtectionDomain().getCodeSource()
    codeSource.getLocation.getPath
  }

  val conf =
    if (yarn)
      new SparkConf().
        setAppName("spark-cdh5-template-yarn").
        set("executor-memory", "128m").
        setJars(List(getJar(AvroSaver.getClass), getJar(classOf[AvroInputFormat[GenericRecord]]))).
        set("spark.yarn.jar", "hdfs:///user/spark/share/lib/spark-assembly.jar").
        setMaster("yarn-client")
    else
      new SparkConf().
        setAppName("spark-cdh5-template-local").
        setMaster("local[16]")

  val sparkContext = new SparkContext(conf)

  import org.apache.spark.sql._

  val sqlContext = new SQLContext(sparkContext)

  val input = if (conf.get("spark.app.name") == "spark-cdh5-template-yarn")
    s"hdfs:///user/${System.getProperty("user.name")}/test.avro"
  else
    s"file://${System.getProperty("user.dir")}/src/test/resources/test.avro"

  import com.databricks.spark.avro._

  val data = sqlContext.avroFile(input)

  data.registerTempTable("test")

  val res = sqlContext.sql("select * from test where a < 10")

  println(res.collect().toList)

  sparkContext.stop()

}