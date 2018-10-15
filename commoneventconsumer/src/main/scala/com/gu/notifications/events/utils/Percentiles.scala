package com.gu.notifications.events.utils

object Percentiles {

  sealed trait PercentilesException extends RuntimeException
  case object InvalidPercentile extends PercentilesException {
    override def getLocalizedMessage: String = s"Percentile should be between 0 and 100"
  }
  case object EmptyValues extends PercentilesException {
    override def getLocalizedMessage: String = s"Cannot calculate percentile of no values"
  }

  def percentile[A](perc: Int)(as: Seq[A])(implicit ord: Ordering[A]): Either[PercentilesException, A] = {
    (perc, as) match {
      case (p, _) if p < 0 || p > 100 => Left(InvalidPercentile)
      case (_, Nil) => Left(EmptyValues)
      case (p, v) =>
        val percIndex = as.length * p / 100
        val maxIndex = as.length - 1
        Right(v.sorted.apply(Math.min(percIndex, maxIndex)))
    }
  }

  def percentileBuckets[A](perc: Int)(buckets: Map[A, Int])(implicit ord: Ordering[A]): Either[PercentilesException, A] = {
    val sortedBuckets = buckets.toList.sortBy(_._1)
    (perc, sortedBuckets) match {
      case (p, _) if p < 0 || p > 100 => Left(InvalidPercentile)
      case (_, Nil) => Left(EmptyValues)
      case (p, v) =>
        val accumulated =
          sortedBuckets
            .foldLeft(List.empty[(A, Int)]) { case (acc , (d, c)) =>
              (d, acc.headOption.fold(0)(_._2) + c) +: acc
            }
            .reverse
        val totalCount = buckets.values.sum
        accumulated.dropWhile(_._2 < totalCount * perc / 100) match {
          case Nil => Left(EmptyValues)
          case head :: _ => Right(head._1)
        }
    }
  }

}

