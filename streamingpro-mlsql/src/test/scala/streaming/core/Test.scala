package streaming.core

import java.net.URL

import org.apache.spark.graphx.{Edge, Graph, VertexId}
import org.apache.spark.rdd.RDD
import org.apache.spark.sql.SparkSession
import org.apache.spark.sql.catalyst.JavaTypeInference
import org.apache.spark.sql.catalyst.analysis.FunctionRegistry._
import org.apache.spark.sql.catalyst.expressions.{Expression, ScalaUDF}
import org.apache.spark.sql.execution.aggregate.ScalaUDAF
import org.apache.spark.sql.expressions.UserDefinedAggregateFunction
import org.apache.spark.sql.types.DataType
import org.apache.spark.util.ScalaSourceCodeCompiler
import org.apache.spark.{SparkConf, SparkContext}
import org.joda.time.DateTime
import org.joda.time.format.ISODateTimeFormat
import org.scalatest.{FlatSpec, Matchers}
import streaming.common.{PunctuationUtils, ScriptCacheKey, SourceCodeCompiler, UnicodeUtils}

import org.apache.spark.sql.types._
import scala.collection.mutable.ArrayBuffer

/**
  * Created by allwefantasy on 28/3/2017.
  */
object Test {
  def main(args: Array[String]): Unit = {
    println(findInputInArrayBracket("array(array(map(string,array(string))))"))
    println(findKeyAndValue("string,map(string,array(string))")._1)
    println(findKeyAndValue("string,map(string,array(string))")._2)
    println(toSparkType("array(array(map(string,array(string))))"))
  }


  //
  private def findInputInArrayBracket(input: String) = {
    val max = input.length - 1
    val rest = ArrayBuffer[Char]()
    var firstS = false
    var fBracketCount = 0
    (0 until max).foreach { i =>
      input(i) match {
        case '(' =>
          if (firstS) {
            rest += input(i)
            fBracketCount += 1
          } else {
            firstS = true
          }
        case ')' => fBracketCount -= 1
          if (fBracketCount < 0) {
            firstS = false
          } else {
            rest += input(i)
          }
        case _ =>
          if (firstS) {
            rest += input(i)
          }

      }
    }
    rest.mkString("")
  }

  private def findKeyAndValue(input: String) = {
    val max = input.length - 1
    var fBracketCount = 0
    var position = 0
    (0 until max).foreach { i =>
      input(i) match {
        case '(' =>
          fBracketCount += 1
        case ')' =>
          fBracketCount -= 1
        case ',' =>
          if (fBracketCount == 0) {
            position = i
          }
        case _ =>
      }
    }
    (input.substring(0, position), input.substring(position + 1))
  }

  //array(array(map(string,string)))
  private def toSparkType(dt: String): DataType = dt match {
    case "boolean" => BooleanType
    case "byte" => ByteType
    case "short" => ShortType
    case "integer" => IntegerType
    case "date" => DateType
    case "long" => LongType
    case "float" => FloatType
    case "double" => DoubleType
    case "decimal" => DoubleType
    case "binary" => BinaryType
    case "string" => StringType
    case c: String if c.startsWith("array") =>
      ArrayType(toSparkType(findInputInArrayBracket(c)))
    case c: String if c.startsWith("map") =>
      //map(map(string,string),string)
      val (key, value) = findKeyAndValue(findInputInArrayBracket(c))
      MapType(toSparkType(key), toSparkType(value))

    case _ => throw new RuntimeException("dt is not found spark type")

  }
}

object UdfUtils {

  def newInstance(clazz: Class[_]): Any = {
    val constructor = clazz.getDeclaredConstructors.head
    constructor.setAccessible(true)
    constructor.newInstance()
  }

  def getMethod(clazz: Class[_], method: String) = {
    val candidate = clazz.getDeclaredMethods.filter(_.getName == method).filterNot(_.isBridge)
    if (candidate.isEmpty) {
      throw new Exception(s"No method $method found in class ${clazz.getCanonicalName}")
    } else if (candidate.length > 1) {
      throw new Exception(s"Multiple method $method found in class ${clazz.getCanonicalName}")
    } else {
      candidate.head
    }
  }

}

case class VeterxAndGroup(vertexId: VertexId, group: VertexId)
