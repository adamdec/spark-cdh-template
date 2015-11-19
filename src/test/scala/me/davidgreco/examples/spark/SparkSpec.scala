/*
 * Copyright 2015 David Greco
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package me.davidgreco.examples.spark

import com.databricks.spark.avro.AvroSaver
import org.apache.avro.generic.GenericRecord
import org.apache.avro.mapred.{ AvroInputFormat, AvroWrapper }
import org.apache.hadoop.conf.Configuration
import org.apache.hadoop.fs.Path
import org.apache.hadoop.io.NullWritable
import org.apache.spark.sql.SQLContext
import org.apache.spark.{ SparkConf, SparkContext }
import org.scalatest.{ BeforeAndAfterAll, MustMatchers, WordSpec }

case class Person(name: String, age: Int)

class SparkSpec extends WordSpec with MustMatchers with BeforeAndAfterAll {

  var sparkContext: SparkContext = _

  override def beforeAll(): Unit = {
    val conf = new SparkConf().
      setAppName("spark-cdh5-template-local-test").
      setMaster("local[16]")
    sparkContext = new SparkContext(conf)
    ()
  }

  "Spark" must {
    "load an avro file as a schema rdd correctly" in {

      val sqlContext = new SQLContext(sparkContext)

      val input = s"file://${System.getProperty("user.dir")}/src/test/resources/test.avro"

      import com.databricks.spark.avro._

      val data = sqlContext.avroFile(input)

      data.registerTempTable("test")

      val res = sqlContext.sql("select * from test where a < 10")

      res.collect().toList.toString must
        be("List([0,CIAO0], [1,CIAO1], [2,CIAO2], [3,CIAO3], [4,CIAO4], [5,CIAO5], [6,CIAO6], [7,CIAO7], [8,CIAO8], [9,CIAO9])")
    }
  }

  "Spark" must {
    "load an avro file as generic records" in {

      val input = s"file://${System.getProperty("user.dir")}/src/test/resources/test.avro"

      val rdd = sparkContext.hadoopFile[AvroWrapper[GenericRecord], NullWritable](
        input,
        classOf[AvroInputFormat[GenericRecord]],
        classOf[AvroWrapper[GenericRecord]],
        classOf[NullWritable]
      )

      val rows = rdd.map(gr => gr._1.datum().get("b").toString)

      rows.first() must be("CIAO0")
    }
  }

  "Spark" must {
    "save an schema rdd as an avro file correctly" in {

      val sqlContext = new SQLContext(sparkContext)

      import sqlContext.implicits._

      val output = s"file://${System.getProperty("user.dir")}/tmp/test.avro"

      //I delete the output in case it exists
      val conf = new Configuration()
      val dir = new Path(output)
      val fileSystem = dir.getFileSystem(conf)
      if (fileSystem.exists(dir))
        fileSystem.delete(dir, true)

      val peopleList: List[Person] = List(Person("David", 50), Person("Ruben", 14), Person("Giuditta", 12), Person("Vita", 19))
      val people = sparkContext.parallelize[Person](peopleList).toDF()
      people.registerTempTable("people")

      val teenagers = sqlContext.sql("SELECT * FROM people WHERE age >= 13 AND age <= 19")
      AvroSaver.save(teenagers, output)

      //Now I reload the file to check if everything is fine
      import com.databricks.spark.avro._
      val data = sqlContext.avroFile(output)
      data.registerTempTable("teenagers")
      sqlContext.sql("select * from teenagers").collect().toList.toString must be("List([Ruben,14], [Vita,19])")
    }
  }

  override def afterAll(): Unit = {
    sparkContext.stop()
  }

}
