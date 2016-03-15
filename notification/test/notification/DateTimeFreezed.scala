package notification

import org.joda.time.DateTimeUtils
import org.specs2.execute.{AsResult, Result}
import org.specs2.specification.AroundEach

trait DateTimeFreezed extends AroundEach {
  def around[R: AsResult](r: => R): Result = {
    DateTimeUtils.setCurrentMillisFixed(DateTimeUtils.currentTimeMillis)
    try AsResult(r)
    finally DateTimeUtils.setCurrentMillisSystem()
  }
}
