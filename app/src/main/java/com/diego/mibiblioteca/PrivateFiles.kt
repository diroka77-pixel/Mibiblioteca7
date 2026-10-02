package com.diego.mibiblioteca

import android.util.AtomicFile
import java.io.File

private val privateFileLock = Any()

/** Keep the previous complete file if writing a replacement fails. */
internal fun File.writeSafely(bytes: ByteArray) = synchronized(privateFileLock) {
    val atomic = AtomicFile(this)
    val stream = atomic.startWrite()
    try {
        stream.write(bytes)
        atomic.finishWrite(stream)
    } catch (failure: Throwable) {
        atomic.failWrite(stream)
        throw failure
    }
}
