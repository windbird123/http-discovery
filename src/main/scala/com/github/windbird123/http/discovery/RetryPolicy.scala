package com.github.windbird123.http.discovery

trait RetryPolicy {
  val waitUntilServerIsAvailable: Boolean = false

  // request 를 보낸 base 주소에서 Timeout 이 발생할 경우 해당 주소로 최대 maxRetryNumberWhenTimeout 번 재시도함
  val maxRetryNumberWhenTimeout: Int = 1

  // request 를 보낸 base 주소가 문제가 있는 것으로 판단될 경우, retryToAnotherAddressAfterSleepMs 후에 다른 base 주소로 재시도
  val retryToAnotherAddressAfterSleepMs: Long = 10000L

  // code, body 를 확인해 isWorthRetryToAnotherAddress 값이 true 로 설정될 경우, 다른 base 주소로 retryToAnotherAddressAfterSleepMs 후에 재시도
  // code 값으로는 http status code 에 -1 (timeout 일때) 값이 추가
  def isWorthRetryToAnotherAddress(code: Int, body: Array[Byte]): Boolean = false
}


object DefaultRetryPolicy extends RetryPolicy
