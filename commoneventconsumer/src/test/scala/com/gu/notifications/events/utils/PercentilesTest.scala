package com.gu.notifications.events.utils

import com.gu.notifications.events.utils.Percentiles.{EmptyValues, InvalidPercentile, PercentilesException}
import org.specs2.mutable.Specification
import com.gu.notifications.events.utils.Percentiles._

class PercentilesTest extends Specification {

  "percentile" should {
    "return correct error if percentile value is less or equal to 0" in {
      percentile(-1)(Seq(1)) should equalTo(Left[PercentilesException, Int](InvalidPercentile))
    }
    "return correct error if percentile value is greater than 100" in {
      percentile(101)(Seq(1)) should equalTo(Left[PercentilesException, Int](InvalidPercentile))
    }
    "return correct error if list of values is empty" in {
      percentile(50)(Seq.empty[Int]) should equalTo(Left[PercentilesException, Int](EmptyValues))
    }
    "return lowest value when asking for the 0th percentile" in {
      percentile(0)(Seq(3, 1, 3)) should equalTo(Right(1))
    }
    "return highest value when asking for the 100th percentile" in {
      percentile(100)(Seq(3, 4, 1, 3)) should equalTo(Right(4))
    }
    "calculate correct values" in {
      val ints = Seq(9, 8, 7, 6, 5, 4, 3, 2, 1)
      percentile(10)(ints) should equalTo(Right(1))
      percentile(20)(ints) should equalTo(Right(2))
      percentile(30)(ints) should equalTo(Right(3))
      percentile(40)(ints) should equalTo(Right(4))
      percentile(50)(ints) should equalTo(Right(5))
      percentile(60)(ints) should equalTo(Right(6))
      percentile(70)(ints) should equalTo(Right(7))
      percentile(80)(ints) should equalTo(Right(8))
      percentile(90)(ints) should equalTo(Right(9))

      percentile(50)(Seq(1, 1, 1, 1, 1, 1, 8)) should equalTo(Right(1))
      percentile(99)(Seq(1, 1, 1, 1, 1, 1, 8)) should equalTo(Right(8))
    }
  }

  "percentileBucket" should {
    "return correct error if percentile value is less or equal to 0" in {
      percentileBuckets(-1)(Map("a" -> 1)) should equalTo(Left[PercentilesException, Int](InvalidPercentile))
    }
    "return correct error if percentile value is greater than 100" in {
      percentileBuckets(101)(Map("a" -> 1)) should equalTo(Left[PercentilesException, Int](InvalidPercentile))
    }
    "return correct error if buckets is empty" in {
      percentileBuckets(50)(Map.empty[String, Int]) should equalTo(Left[PercentilesException, Int](EmptyValues))
    }
    "return lowest bucket when asking for the 0th percentile" in {
      percentileBuckets(0)(Map("a" -> 1, "b" -> 1, "c" -> 1)) should equalTo(Right("a"))
    }
    "return highest bucket when asking for the 100th percentile" in {
      percentileBuckets(100)(Map("a" -> 1, "b" -> 1, "c" -> 1, "d" -> 1)) should equalTo(Right("d"))
    }
    "return correct bucket" in {
      val buckets = Map("a" -> 1, "b" -> 1, "c" -> 1, "d" -> 1, "e" -> 1, "f" -> 1, "g" -> 1, "h" -> 1, "i" -> 1, "j" -> 1)
      percentileBuckets(10)(buckets) should equalTo(Right("a"))
      percentileBuckets(20)(buckets) should equalTo(Right("b"))
      percentileBuckets(30)(buckets) should equalTo(Right("c"))
      percentileBuckets(40)(buckets) should equalTo(Right("d"))
      percentileBuckets(50)(buckets) should equalTo(Right("e"))
      percentileBuckets(60)(buckets) should equalTo(Right("f"))
      percentileBuckets(70)(buckets) should equalTo(Right("g"))
      percentileBuckets(80)(buckets) should equalTo(Right("h"))
      percentileBuckets(90)(buckets) should equalTo(Right("i"))

      val moreBuckets = Map("a" -> 1, "b" -> 1, "c" -> 1, "d" -> 1, "e" -> 1, "f" -> 5)
      percentileBuckets(50)(moreBuckets) should equalTo(Right("e"))
      percentileBuckets(99)(moreBuckets) should equalTo(Right("f"))
    }
  }

}
