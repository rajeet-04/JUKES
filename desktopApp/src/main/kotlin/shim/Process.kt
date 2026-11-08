package android.os

object Process { fun myPid(): Int = ProcessHandle.current().pid().toInt() }
