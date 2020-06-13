package com.github.windbird123.http.discovery

import zio.duration._
import zio.test.Assertion._
import zio.test.environment.TestClock
import zio.test.{DefaultRunnableSpec, ZSpec, _}
import zio.{Ref, Task}

object AddressFactoryTest extends DefaultRunnableSpec {
  override def spec: ZSpec[_root_.zio.test.environment.TestEnvironment, Any] =
    suite("AddressFactory Test")(chooseSuite)

  val addressDiscover: AddressDiscover = new AddressDiscover {
    override def fetch(): Task[Seq[String]] = Task.effect(Seq.empty[String])
  }

  val chooseSuite = suite("choose")(
    testM("ref element 개수가 1개 일때, 해당 element 가 선택되어야 한다.") {
      for {
        ref     <- Ref.make(Seq("http://a.b.c"))
        factory = new AddressFactory(ref, addressDiscover)
        one     <- factory.choose(false)
      } yield assert(one)(equalTo("http://a.b.c"))
    },
    testM("ref element 개수가 0 개이고 waitUntilServerIsAvailable=false 일 때, fail 되어야 한다.") {
      for {
        ref     <- Ref.make(Seq.empty[String])
        factory = new AddressFactory(ref, addressDiscover)
        failed  <- factory.choose(false).flip
      } yield assert(failed)(isSubtype[Exception](anything))
    },
    testM("ref element 개수가 0 이지만 2초후에 채워지면, 6초가 지난 뒤에는 해당 주소가 선택되어야 한다.") {
      for {
        ref     <- Ref.make(Seq.empty[String])
        factory = new AddressFactory(ref, addressDiscover)
        oneFork <- factory.choose(true).fork
        _       <- ref.set(Seq("http://a.b.c")).delay(2.seconds).fork
        _       <- TestClock.adjust(6.seconds)
        one     <- oneFork.join
      } yield assert(one)(equalTo("http://a.b.c"))
    }
  )
}
