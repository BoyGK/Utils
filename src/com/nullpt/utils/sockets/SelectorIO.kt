package com.nullpt.utils.sockets

interface SelectorIO {

    fun accept(code: Int) {}

    fun connect(code: Int) {}

    fun receive(code: Int, byteArray: ByteArray)

}