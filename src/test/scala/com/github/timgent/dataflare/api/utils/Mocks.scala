package com.github.timgent.dataflare.api.utils

import zio.logging.{LogFormat, LogLevel, Logging}

object Mocks {
  def mockLogger = {
    (Logging.console(
      logLevel = LogLevel.Info,
      format = LogFormat.ColoredLogFormat()
    ) >>> Logging.withRootLoggerName("data-flare-api-tests"))
  }
}
