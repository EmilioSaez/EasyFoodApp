package com.easyfood.transfer

import kotlinx.cinterop.ExperimentalForeignApi
import platform.posix.arc4random_uniform

@OptIn(ExperimentalForeignApi::class)
internal actual fun secureRandomInt(bound: Int): Int = arc4random_uniform(bound.toUInt()).toInt()
