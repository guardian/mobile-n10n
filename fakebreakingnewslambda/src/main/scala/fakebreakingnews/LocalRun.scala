package fakebreakingnews

object LocalRun extends App {
  private val fakeBreakingNewsLambda = new FakeBreakingNewsLambda()
  fakeBreakingNewsLambda.handleRequest()
}
