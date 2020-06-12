package com.github.windbird123.http.discovery

import scalaj.http.Http
import zio.{Task, UIO}

import scala.util.{Failure, Success, Try}

object BlockingSmartClientSample {
  def main(args: Array[String]): Unit = {
    val addressDiscover = new AddressDiscover.Service {
      override val periodSec: Long = 300L
      override def fetch(configString: String): Task[Seq[String]] =
        UIO(Seq("https://jsonplaceholder.typicode.com"))
    }

    val retryPolicy = new RetryPolicy.Service {
      override val waitUntilServerIsAvailable: Boolean                                      = true
      override val maxRetryNumberWhenTimeout: Int                                           = 5
      override val retryToAnotherAddressAfterSleepMs: Long                                  = 10000L
      override def isWorthRetryToAnotherAddress(code: Int, body: Array[Byte]): UIO[Boolean] = UIO.succeed(false)
    }

    val client = BlockingSmartClient.create(addressDiscover)
    val response = Try {
      client.execute(Http("/todos/1").timeout(2000, 2000), retryPolicy)
    }

    response match {
      case Success(value) =>
        val (code, body) = value
        println(code)
        println(new String(body, io.Codec.UTF8.name))
      case Failure(e) =>
        e.printStackTrace()
    }
  }
}
