package com.github.windbird123.http.discovery

import java.net.SocketTimeoutException

import com.typesafe.scalalogging.LazyLogging
import scalaj.http.HttpRequest
import zio._
import zio.clock.Clock
import zio.duration._

trait HttpAction {
  def tryExecute(r: HttpRequest, maxRetryNumberWhenTimeout: Int): Task[(Int, Array[Byte])]
}

object DefaultHttpAction extends HttpAction with LazyLogging {
  override def tryExecute(r: HttpRequest, maxRetryNumberWhenTimeout: Int): Task[(Int, Array[Byte])] = {
    val schedule: Schedule[Clock, Throwable, ((Duration, Int), Throwable)] = {
      Schedule.exponential(1.second) && Schedule.recurs(maxRetryNumberWhenTimeout) && Schedule.doWhile[Throwable] {
        case e: SocketTimeoutException => {
          logger.info(s"Retry, url=[${r.url}] cause=[${e.getMessage}]")
          true
        }
        case t: Throwable => {
          logger.info(s"Fail, url=[${r.url}]", t)
          false
        }
      }
    }

    blocking.effectBlocking {
      val res = r.asBytes
      (res.code, res.body)
    }.retry(schedule).provideLayer(ZEnv.live)
  }
}
