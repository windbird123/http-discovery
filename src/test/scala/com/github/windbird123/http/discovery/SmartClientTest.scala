package com.github.windbird123.http.discovery

import java.util.concurrent.atomic.AtomicInteger

import scalaj.http.{Http, HttpRequest}
import zio._
import zio.blocking.Blocking
import zio.clock.Clock
import zio.duration._
import zio.random.Random
import zio.test.Assertion._
import zio.test.TestAspect._
import zio.test.environment.TestClock
import zio.test.{DefaultRunnableSpec, ZSpec, _}

object SmartClientTest extends DefaultRunnableSpec {
  override def spec: ZSpec[_root_.zio.test.environment.TestEnvironment, Any] = suite("SmartClient Test")(executeSuite)

  val successHttpActionLayer: Layer[Nothing, Has[HttpAction.Service]] = ZLayer.succeed(new HttpAction.Service {
    override def tryExecute(r: HttpRequest, maxRetryNumberWhenTimeout: Int): Task[(Int, Array[Byte])] =
      Task.succeed((200, "success".getBytes(io.Codec.UTF8.name)))
  })

  val executeSuite = suite("execute")(
    testM("waitUntilServerIsAvailable=false 이고 사용 가능한 주소가 없을때 fail 되어야 한다.") {
      val addressDiscover: Layer[Nothing, Has[AddressDiscover.Service]] = ZLayer.succeed(new AddressDiscover.Service {
        override def fetch(): Task[Seq[String]] = Task.succeed(Seq.empty[String])
      })

      val retryPolicy: Layer[Nothing, Has[RetryPolicy.Service]] = ZLayer.succeed(new RetryPolicy.Service {
        override val waitUntilServerIsAvailable: Boolean = false
      })

      val layer = retryPolicy ++ addressDiscover ++ successHttpActionLayer

      val scn = for {
        client <- SmartClient.create()
        failed <- client.execute(Http("/some/path")).flip
      } yield failed

      for {
        r <- scn.provideCustomLayer(layer)
      } yield assert(r)(isSubtype[Throwable](anything))
    } @@ ignore,
    testM("kk") {
      val addressDiscover: Layer[Nothing, Has[AddressDiscover.Service]] = ZLayer.succeed(new AddressDiscover.Service {
        val n                        = new AtomicInteger(0)
        override val periodSec: Long = 1
        override def fetch(): Task[Seq[String]] =
          if (n.addAndGet(1) < 3) Task.succeed(Seq.empty[String]) else Task.succeed(Seq("http://a.b.c"))
      })

      val retryPolicy: Layer[Nothing, Has[RetryPolicy.Service]] = ZLayer.succeed(new RetryPolicy.Service {
        override val retryToAnotherAddressAfterSleepMs: Long = 1000L
        override val waitUntilServerIsAvailable: Boolean     = true
      })

      val layer = retryPolicy ++ addressDiscover ++ successHttpActionLayer

      val scn = for {
        client  <- SmartClient.create()
        resFork <- client.execute(Http("/some/path")).fork
        _       <- TestClock.adjust(5.seconds)
        res     <- resFork.join
      } yield res

      for {
        r <- scn.provideSomeLayer[TestClock with Blocking with Clock with Random](layer)
      } yield assert(r._1)(equalTo(200))
    }
  )
}
