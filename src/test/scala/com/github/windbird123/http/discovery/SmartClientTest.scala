package com.github.windbird123.http.discovery

import java.net.{SocketException, SocketTimeoutException}
import java.util.concurrent.atomic.AtomicInteger

import scalaj.http.{Http, HttpRequest}
import zio._
import zio.duration._
import zio.test.Assertion._
import zio.test.environment.TestClock
import zio.test.{DefaultRunnableSpec, ZSpec, _}

object SmartClientTest extends DefaultRunnableSpec {
  override def spec: ZSpec[_root_.zio.test.environment.TestEnvironment, Any] = suite("SmartClient Test")(executeSuite)

  val successHttpAction: HttpAction = new HttpAction {
    override def tryExecute(r: HttpRequest, maxRetryNumberWhenTimeout: Int): Task[(Int, Array[Byte])] =
      Task.succeed((200, "success".getBytes(io.Codec.UTF8.name)))
  }

  val executeSuite = suite("execute")(
    testM("waitUntilServerIsAvailable=false 이고 사용 가능한 주소가 없을때 fail 되어야 한다.") {
      val addressDiscover = new AddressDiscover {
        override def fetch(): Task[Seq[String]] = Task.succeed(Seq.empty[String])
      }

      val retryPolicy = new RetryPolicy {
        override val waitUntilServerIsAvailable: Boolean = false
      }

      for {
        client <- SmartClient.create(addressDiscover, successHttpAction)
        failed <- client.execute(Http("/some/path"), retryPolicy).flip
      } yield assert(failed)(isSubtype[Throwable](anything))
    },
    /**
     * 주의: AddressDiscover.fetch() 를 구현할때 아래와 같은 형태로 구현하면 안된다.
     *   if (..) Task(..) else Task(..)
     * ZIO schedule 은 effect 만을 주기적으로 수행한다 !!!
     */
    testM("waitUntilServerIsAvailable=true 이면, 사용 가능한 주소가 있을 때 까지 기다렸다 수행하도록 한다.") {
      val addressDiscover = new AddressDiscover {
        val tryCount                 = new AtomicInteger(0)
        override val periodSec: Long = 1L
        override def fetch(): Task[Seq[String]] = Task {
          if (tryCount.getAndIncrement() < 3) Seq.empty[String] else Seq("http://a.b.c")
        }
      }

      val retryPolicy = new RetryPolicy {
        override val retryToAnotherAddressAfterSleepMs: Long = 1000L
        override val waitUntilServerIsAvailable: Boolean     = true
      }

      for {
        client  <- SmartClient.create(addressDiscover, successHttpAction)
        resFork <- client.execute(Http("/some/path"), retryPolicy).fork
        _       <- TestClock.adjust(5.seconds)
        res     <- resFork.join

      } yield assert(res._1)(equalTo(200)) && assert(res._2)(equalTo("success".getBytes(io.Codec.UTF8.name)))
    },
    testM("SocketException 을 발생시키는 bad address 가 있으면, 이를 제거해 나가면서 request 를 시도한다.") {
      val addressDiscover = new AddressDiscover {
        override def fetch(): Task[Seq[String]] =
          Task.succeed(
            Seq(
              "http://bad1",
              "http://bad2",
              "http://bad3",
              "http://bad4",
              "http://bad5",
              "http://bad6",
              "http://bad7",
              "http://bad8",
              "http://bad9",
              "http://good"
            )
          )
      }

      val retryPolicy = new RetryPolicy {
        override val maxRetryNumberWhenTimeout: Int          = 1
        override val retryToAnotherAddressAfterSleepMs: Long = 1000L
      }

      val httpAction = new HttpAction {
        override def tryExecute(r: HttpRequest, maxRetryNumberWhenTimeout: Int): Task[(Int, Array[Byte])] =
          for {
            _       <- ZIO.when(!r.url.startsWith("http://good"))(Task.fail(new SocketException("bad")))
            success <- Task.succeed((200, "success".getBytes(io.Codec.UTF8.name)))
          } yield success
      }

      for {
        client  <- SmartClient.create(addressDiscover, httpAction)
        resFork <- client.execute(Http("/some/path"), retryPolicy).fork
        _       <- TestClock.adjust(100.seconds)
        res     <- resFork.join
      } yield assert(res._1)(equalTo(200)) && assert(res._2)(equalTo("success".getBytes(io.Codec.UTF8.name)))
    },
    testM("HttpAction 의 tryExecute 의 최종 결과가 SocketTimeoutException 일 경우, request 문제로 보고 fail 되어야 한다.") {
      val addressDiscover = new AddressDiscover {
        override def fetch(): Task[Seq[String]] = Task.succeed(Seq("http://a.b.c"))
      }

      val retryPolicy = new RetryPolicy {
        override val maxRetryNumberWhenTimeout: Int          = 5
        override val retryToAnotherAddressAfterSleepMs: Long = 1000L
      }

      val httpAction = new HttpAction {
        override def tryExecute(r: HttpRequest, maxRetryNumberWhenTimeout: Int): Task[(Int, Array[Byte])] =
          ZIO.fail(new SocketTimeoutException())
      }

      for {
        client  <- SmartClient.create(addressDiscover, httpAction)
        resFork <- client.execute(Http("/some/path"), retryPolicy).fork
        _       <- TestClock.adjust(100.seconds)
        failed  <- resFork.join.flip
      } yield assert(failed)(isSubtype[SocketTimeoutException](anything))
    },
    testM("isWorthRetryToAnotherAddress 에서 설정된 정책대로 retry 가 잘 수행되어야 한다.") {
      val addressDiscover = new AddressDiscover {
        override def fetch(): Task[Seq[String]] =
          Task.succeed(
            Seq(
              "http://bad1",
              "http://bad2",
              "http://bad3",
              "http://bad4",
              "http://bad5",
              "http://bad6",
              "http://bad7",
              "http://bad8",
              "http://bad9",
              "http://good"
            )
          )
      }

      val retryPolicy = new RetryPolicy {
        override val maxRetryNumberWhenTimeout: Int          = 1
        override val retryToAnotherAddressAfterSleepMs: Long = 1000L
        override def isWorthRetryToAnotherAddress(code: Int, body: Array[Byte]): Boolean =
          if (code == 503) true else false
      }

      val httpAction = new HttpAction {
        override def tryExecute(r: HttpRequest, maxRetryNumberWhenTimeout: Int): Task[(Int, Array[Byte])] = UIO {
          if (r.url.startsWith("http://good")) {
            (200, "success".getBytes(io.Codec.UTF8.name))
          } else {
            (503, Array.empty[Byte])
          }
        }
      }

      for {
        client  <- SmartClient.create(addressDiscover, httpAction)
        resFork <- client.execute(Http("/some/path"), retryPolicy).fork
        _       <- TestClock.adjust(100.seconds)
        res     <- resFork.join
      } yield assert(res._1)(equalTo(200)) && assert(res._2)(equalTo("success".getBytes(io.Codec.UTF8.name)))
    },
    testM("AddressDiscover.fetch() 가 실패하면, 기존 주소 그대로 유지되어야 한다.") {
      val addressDiscover = new AddressDiscover {
        val tryCount = new AtomicInteger(0)

        override val periodSec: Long = 1L

        // 최초 설정 이후에는 계속 주소 가져오는 것을 실패하도록 함
        override def fetch(): Task[Seq[String]] =
          for {
            count <- UIO(tryCount.getAndIncrement())
            _ <- ZIO.when(count >= 2) {
                  Task.fail(new Exception("some exception !!!"))
                }
            success <- ZIO.succeed(Seq("http://good"))
          } yield success
      }

      val retryPolicy = new RetryPolicy {
        override val waitUntilServerIsAvailable: Boolean = false
      }

      for {
        client <- SmartClient.create(addressDiscover, successHttpAction)
        _      <- TestClock.adjust(5.seconds)
        res    <- client.execute(Http("/some/path"), retryPolicy)
      } yield assert(res._1)(equalTo(200))
    }
  )
}
