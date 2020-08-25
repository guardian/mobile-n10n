package com.gu.mobile.notifications.football.models

import pa.{MatchDay, MatchEvent}

case class RawMatchData(
  matchDay: MatchDay,
  allEvents: List[MatchEvent]
) {
  def withArticle(articleId: Option[String]): MatchDataWithArticle = MatchDataWithArticle(
    matchDay = matchDay,
    allEvents = allEvents,
    articleId = articleId
  )
}

case class MatchDataWithArticle(
  matchDay: MatchDay,
  allEvents: List[MatchEvent],
  articleId: Option[String]
)
