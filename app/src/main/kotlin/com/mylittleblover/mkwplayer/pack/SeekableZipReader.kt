package com.mylittleblover.mkwplayer.pack

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.SeekableByteChannel
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.zip.CRC32
import java.util.zip.Inflater
import java.util.zip.InflaterInputStream
import java.util.zip.ZipException

/**
 * Random-access ZIP / ZIP64 reader over any [SeekableByteChannel] (#96).
 *
 * Allows reading Storage Access Framework `ParcelFileDescriptor` channels directly from user
 * storage without copying multi-gigabyte `.mkwpack` archives into app cache storage first.
 */
internal class SeekableZipReader private constructor(
    private val channel: SeekableByteChannel,
    val fileSize: Long,
    val entries: List<Entry>,
    val archiveFingerprint: String,
    private val ownsChannel: Boolean,
) : Closeable {

    data class Entry(
        val name: String,
        val compressionMethod: Int,
        val crc32: Long,
        val compressedSize: Long,
        val uncompressedSize: Long,
        val localHeaderOffset: Long,
        val isDirectory: Boolean,
    )

    private val channelLock = Any()
    private val entryByName: Map<String, Entry> = entries.associateBy { it.name }

    fun memberNames(): List<String> = entries.filterNot { it.isDirectory }.map { it.name }

    fun findEntry(name: String): Entry? = entryByName[name]

    fun readBytes(name: String, maxBytes: Long = Long.MAX_VALUE): ByteArray? {
        val entry = entryByName[name] ?: return null
        if (entry.uncompressedSize > maxBytes) {
            throw ZipException("ZIP entry '$name' exceeds allowed limit ($maxBytes bytes).")
        }
        return openStream(entry).use { it.readBytes() }
    }

    fun readText(name: String, maxBytes: Long = 32L * 1024L * 1024L): String? {
        val raw = readBytes(name, maxBytes) ?: return null
        return raw.toString(StandardCharsets.UTF_8)
    }

    fun openStream(entry: Entry): InputStream {
        if (entry.compressionMethod != METHOD_STORED && entry.compressionMethod != METHOD_DEFLATED) {
            throw ZipException(
                "Archive member '${entry.name}' uses unsupported ZIP compression method " +
                    "${entry.compressionMethod}. Only STORED (0) and DEFLATED (8) are supported.",
            )
        }
        val payloadOffset = resolvePayloadOffset(entry)
        val compressedStream = ChannelSliceInputStream(
            channel = channel,
            lock = channelLock,
            startOffset = payloadOffset,
            length = entry.compressedSize,
        )
        val rawStream: InputStream = if (entry.compressionMethod == METHOD_STORED) {
            compressedStream
        } else {
            // Close the underlying Inflater cleanly when the caller closes the returned stream.
            val inflater = Inflater(true)
            object : InflaterInputStream(compressedStream, inflater, STREAM_BUFFER_BYTES) {
                override fun close() {
                    try {
                        super.close()
                    } finally {
                        inflater.end()
                    }
                }
            }
        }
        return VerifiedEntryInputStream(
            delegate = rawStream,
            entryName = entry.name,
            expectedCrc32 = entry.crc32,
            expectedSize = entry.uncompressedSize,
        )
    }

    private fun resolvePayloadOffset(entry: Entry): Long {
        val header = ByteArray(LOCAL_FILE_HEADER_BYTES)
        synchronized(channelLock) {
            readFullyAt(channel, entry.localHeaderOffset, header, 0, header.size)
        }
        val buf = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)
        val sig = buf.getInt(0)
        if (sig != LOCAL_FILE_HEADER_SIG) {
            throw ZipException("Invalid local file header signature for '${entry.name}'.")
        }
        val nameLen = buf.getShort(26).toInt() and 0xFFFF
        val extraLen = buf.getShort(28).toInt() and 0xFFFF
        return entry.localHeaderOffset + LOCAL_FILE_HEADER_BYTES + nameLen + extraLen
    }

    override fun close() {
        if (ownsChannel) {
            runCatching { channel.close() }
        }
    }

    private class ChannelSliceInputStream(
        private val channel: SeekableByteChannel,
        private val lock: Any,
        startOffset: Long,
        length: Long,
    ) : InputStream() {
        private var cursor = startOffset
        private var remaining = length
        private val singleByte = ByteArray(1)

        override fun read(): Int {
            val count = read(singleByte, 0, 1)
            return if (count <= 0) -1 else singleByte[0].toInt() and 0xFF
        }

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            if (len == 0) return 0
            if (remaining <= 0L) return -1
            val toRead = minOf(len.toLong(), remaining).toInt()
            val buf = ByteBuffer.wrap(b, off, toRead)
            val n = synchronized(lock) {
                channel.position(cursor)
                var totalRead = 0
                while (buf.hasRemaining()) {
                    val read = channel.read(buf)
                    if (read < 0) break
                    totalRead += read
                }
                totalRead
            }
            if (n <= 0) {
                throw EOFException("Unexpected end of ZIP payload stream.")
            }
            cursor += n
            remaining -= n
            return n
        }
    }

    private class VerifiedEntryInputStream(
        private val delegate: InputStream,
        private val entryName: String,
        private val expectedCrc32: Long,
        private val expectedSize: Long,
    ) : InputStream() {
        private val crc = CRC32()
        private var bytesRead = 0L
        private var verified = false
        private val one = ByteArray(1)

        override fun read(): Int {
            val n = read(one, 0, 1)
            return if (n < 0) -1 else one[0].toInt() and 0xFF
        }

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            if (len == 0) return 0
            val n = delegate.read(b, off, len)
            if (n < 0) {
                verifyAtEof()
                return -1
            }
            crc.update(b, off, n)
            bytesRead += n
            if (bytesRead > expectedSize) {
                throw ZipException(
                    "Archive member '$entryName' exceeded declared uncompressed size ($expectedSize).",
                )
            }
            return n
        }

        private fun verifyAtEof() {
            if (verified) return
            verified = true
            if (bytesRead != expectedSize) {
                throw ZipException(
                    "Archive member '$entryName' ended at $bytesRead bytes (expected $expectedSize).",
                )
            }
            val actualCrc = crc.value
            if (actualCrc != expectedCrc32) {
                throw ZipException(
                    "Archive member '$entryName' failed CRC-32 check " +
                        "(expected ${expectedCrc32.toString(16)}, got ${actualCrc.toString(16)}).",
                )
            }
        }

        override fun close() {
            delegate.close()
        }
    }

    companion object {
        private const val METHOD_STORED = 0
        private const val METHOD_DEFLATED = 8

        private const val EOCD_SIG = 0x06054b50
        private const val ZIP64_EOCD_LOCATOR_SIG = 0x07064b50
        private const val ZIP64_EOCD_SIG = 0x06064b50
        private const val CD_FILE_HEADER_SIG = 0x02014b50
        private const val LOCAL_FILE_HEADER_SIG = 0x04034b50

        private const val EOCD_MIN_BYTES = 22
        private const val MAX_EOCD_COMMENT_BYTES = 65_535
        private const val ZIP64_LOCATOR_BYTES = 20
        private const val ZIP64_EOCD_MIN_BYTES = 56
        private const val CD_HEADER_MIN_BYTES = 46
        private const val LOCAL_FILE_HEADER_BYTES = 30
        private const val ZIP64_EXTRA_HEADER_ID = 0x0001
        private const val FLAG_UTF8 = 1 shl 11
        private const val STREAM_BUFFER_BYTES = 64 * 1024
        private const val FINGERPRINT_SLICE_BYTES = 64 * 1024

        fun open(
            channel: SeekableByteChannel,
            ownsChannel: Boolean = true,
        ): SeekableZipReader {
            val size = channel.size()
            if (size < EOCD_MIN_BYTES) {
                throw ZipException("File is too small to be a ZIP archive ($size bytes).")
            }

            val tailWindow = minOf(size, (EOCD_MIN_BYTES + MAX_EOCD_COMMENT_BYTES).toLong()).toInt()
            val tailOffset = size - tailWindow
            val tailBytes = ByteArray(tailWindow)
            readFullyAt(channel, tailOffset, tailBytes, 0, tailWindow)

            val eocdRel = findEocdOffset(tailBytes)
                ?: throw ZipException("Could not find ZIP End of Central Directory record.")
            val eocdAbs = tailOffset + eocdRel
            val eocdBuf = ByteBuffer.wrap(tailBytes, eocdRel, tailWindow - eocdRel)
                .slice()
                .order(ByteOrder.LITTLE_ENDIAN)

            val diskNumber = eocdBuf.getShort(4).toInt() and 0xFFFF
            val cdStartDisk = eocdBuf.getShort(6).toInt() and 0xFFFF
            if (diskNumber != 0 || cdStartDisk != 0) {
                throw ZipException("Multi-disk split ZIP archives are not supported.")
            }

            var cdEntryCount = (eocdBuf.getShort(10).toLong() and 0xFFFFL)
            var cdSize = eocdBuf.getInt(12).toLong() and 0xFFFF_FFFFL
            var cdOffset = eocdBuf.getInt(16).toLong() and 0xFFFF_FFFFL

            val needsZip64 = cdEntryCount == 0xFFFFL ||
                cdSize == 0xFFFF_FFFFL ||
                cdOffset == 0xFFFF_FFFFL

            val locatorOffset = eocdAbs - ZIP64_LOCATOR_BYTES
            if (needsZip64 || locatorOffset >= 0) {
                if (locatorOffset >= 0) {
                    val loc = ByteArray(ZIP64_LOCATOR_BYTES)
                    readFullyAt(channel, locatorOffset, loc, 0, loc.size)
                    val locBuf = ByteBuffer.wrap(loc).order(ByteOrder.LITTLE_ENDIAN)
                    if (locBuf.getInt(0) == ZIP64_EOCD_LOCATOR_SIG) {
                        val zip64EocdOffset = locBuf.getLong(8)
                        if (zip64EocdOffset < 0 || zip64EocdOffset + ZIP64_EOCD_MIN_BYTES > locatorOffset) {
                            throw ZipException("Invalid ZIP64 EOCD offset: $zip64EocdOffset.")
                        }
                        val z64 = ByteArray(ZIP64_EOCD_MIN_BYTES)
                        readFullyAt(channel, zip64EocdOffset, z64, 0, z64.size)
                        val z64Buf = ByteBuffer.wrap(z64).order(ByteOrder.LITTLE_ENDIAN)
                        if (z64Buf.getInt(0) != ZIP64_EOCD_SIG) {
                            throw ZipException("Invalid ZIP64 EOCD signature.")
                        }
                        cdEntryCount = z64Buf.getLong(32)
                        cdSize = z64Buf.getLong(40)
                        cdOffset = z64Buf.getLong(48)
                    } else if (needsZip64) {
                        throw ZipException("ZIP64 sentinel present without ZIP64 EOCD locator.")
                    }
                } else if (needsZip64) {
                    throw ZipException("ZIP64 sentinel present in truncated archive.")
                }
            }

            if (cdOffset < 0 || cdSize < 0 || cdOffset + cdSize > size || cdSize > Int.MAX_VALUE) {
                throw ZipException("Invalid Central Directory bounds (offset=$cdOffset, size=$cdSize, file=$size).")
            }

            val cdBytes = ByteArray(cdSize.toInt())
            readFullyAt(channel, cdOffset, cdBytes, 0, cdBytes.size)
            val parsedEntries = parseCentralDirectory(cdBytes, cdEntryCount)
            val fingerprint = computeFingerprint(channel, size, cdBytes)
            return SeekableZipReader(
                channel = channel,
                fileSize = size,
                entries = parsedEntries,
                archiveFingerprint = fingerprint,
                ownsChannel = ownsChannel,
            )
        }

        private fun findEocdOffset(tail: ByteArray): Int? {
            val buf = ByteBuffer.wrap(tail).order(ByteOrder.LITTLE_ENDIAN)
            for (pos in tail.size - EOCD_MIN_BYTES downTo 0) {
                if (buf.getInt(pos) == EOCD_SIG) {
                    val commentLen = buf.getShort(pos + 20).toInt() and 0xFFFF
                    if (pos + EOCD_MIN_BYTES + commentLen == tail.size) {
                        return pos
                    }
                }
            }
            return null
        }

        private fun parseCentralDirectory(cdBytes: ByteArray, expectedEntries: Long): List<Entry> {
            val out = ArrayList<Entry>(expectedEntries.coerceAtMost(65_536L).toInt())
            val buf = ByteBuffer.wrap(cdBytes).order(ByteOrder.LITTLE_ENDIAN)
            var cursor = 0
            while (cursor < cdBytes.size) {
                if (cursor + CD_HEADER_MIN_BYTES > cdBytes.size) {
                    throw ZipException("Truncated ZIP Central Directory header.")
                }
                val sig = buf.getInt(cursor)
                if (sig != CD_FILE_HEADER_SIG) {
                    throw ZipException("Unexpected signature in Central Directory at $cursor: 0x${sig.toString(16)}")
                }
                val flags = buf.getShort(cursor + 8).toInt() and 0xFFFF
                val method = buf.getShort(cursor + 10).toInt() and 0xFFFF
                val crc32 = buf.getInt(cursor + 16).toLong() and 0xFFFF_FFFFL
                var compSize = buf.getInt(cursor + 20).toLong() and 0xFFFF_FFFFL
                var uncompSize = buf.getInt(cursor + 24).toLong() and 0xFFFF_FFFFL
                val nameLen = buf.getShort(cursor + 28).toInt() and 0xFFFF
                val extraLen = buf.getShort(cursor + 30).toInt() and 0xFFFF
                val commentLen = buf.getShort(cursor + 32).toInt() and 0xFFFF
                var localOffset = buf.getInt(cursor + 42).toLong() and 0xFFFF_FFFFL

                val nameStart = cursor + CD_HEADER_MIN_BYTES
                val extraStart = nameStart + nameLen
                val next = extraStart + extraLen + commentLen
                if (next > cdBytes.size) {
                    throw ZipException("ZIP Central Directory record overflows directory bounds.")
                }

                val charset = if ((flags and FLAG_UTF8) != 0) StandardCharsets.UTF_8 else StandardCharsets.ISO_8859_1
                val rawName = String(cdBytes, nameStart, nameLen, charset)

                if (compSize == 0xFFFF_FFFFL || uncompSize == 0xFFFF_FFFFL || localOffset == 0xFFFF_FFFFL) {
                    val zip64 = readZip64Extra(cdBytes, extraStart, extraLen)
                    var extraPos = 0
                    if (uncompSize == 0xFFFF_FFFFL) {
                        uncompSize = zip64.getOrNull(extraPos++)
                            ?: throw ZipException("Missing ZIP64 uncompressed size for '$rawName'.")
                    }
                    if (compSize == 0xFFFF_FFFFL) {
                        compSize = zip64.getOrNull(extraPos++)
                            ?: throw ZipException("Missing ZIP64 compressed size for '$rawName'.")
                    }
                    if (localOffset == 0xFFFF_FFFFL) {
                        localOffset = zip64.getOrNull(extraPos)
                            ?: throw ZipException("Missing ZIP64 header offset for '$rawName'.")
                    }
                }

                val isDir = rawName.endsWith("/")
                out += Entry(
                    name = rawName,
                    compressionMethod = method,
                    crc32 = crc32,
                    compressedSize = compSize,
                    uncompressedSize = uncompSize,
                    localHeaderOffset = localOffset,
                    isDirectory = isDir,
                )
                cursor = next
            }
            return out
        }

        private fun readZip64Extra(cdBytes: ByteArray, start: Int, len: Int): List<Long> {
            val end = start + len
            val buf = ByteBuffer.wrap(cdBytes).order(ByteOrder.LITTLE_ENDIAN)
            var p = start
            while (p + 4 <= end) {
                val id = buf.getShort(p).toInt() and 0xFFFF
                val size = buf.getShort(p + 2).toInt() and 0xFFFF
                val dataStart = p + 4
                val dataEnd = minOf(end, dataStart + size)
                if (id == ZIP64_EXTRA_HEADER_ID) {
                    val values = ArrayList<Long>(3)
                    var q = dataStart
                    while (q + 8 <= dataEnd) {
                        values += buf.getLong(q)
                        q += 8
                    }
                    return values
                }
                p = dataStart + size
            }
            return emptyList()
        }

        private fun computeFingerprint(
            channel: SeekableByteChannel,
            fileSize: Long,
            cdBytes: ByteArray,
        ): String {
            val md = MessageDigest.getInstance("SHA-256")
            md.update(ByteBuffer.allocate(8).order(ByteOrder.BIG_ENDIAN).putLong(fileSize).array())
            val headLen = minOf(fileSize, FINGERPRINT_SLICE_BYTES.toLong()).toInt()
            if (headLen > 0) {
                val head = ByteArray(headLen)
                readFullyAt(channel, 0L, head, 0, headLen)
                md.update(head)
            }
            val tailLen = minOf(fileSize, FINGERPRINT_SLICE_BYTES.toLong()).toInt()
            if (tailLen > 0) {
                val tail = ByteArray(tailLen)
                readFullyAt(channel, fileSize - tailLen, tail, 0, tailLen)
                md.update(tail)
            }
            md.update(cdBytes)
            return md.digest().joinToString("") { "%02x".format(it) }
        }

        private fun readFullyAt(
            channel: SeekableByteChannel,
            offset: Long,
            dest: ByteArray,
            destOffset: Int,
            length: Int,
        ) {
            if (length == 0) return
            channel.position(offset)
            val buf = ByteBuffer.wrap(dest, destOffset, length)
            while (buf.hasRemaining()) {
                val n = channel.read(buf)
                if (n < 0) {
                    throw EOFException("Unexpected EOF reading $length bytes at offset $offset.")
                }
            }
        }
    }
}
