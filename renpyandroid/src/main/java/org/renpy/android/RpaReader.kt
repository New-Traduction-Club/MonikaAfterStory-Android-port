package org.renpy.android

import net.razorvine.pickle.Unpickler
import java.io.File
import java.io.RandomAccessFile
import java.io.ByteArrayInputStream
import java.util.zip.InflaterInputStream
import java.io.IOException

data class RpaEntry(
    val path: String,
    val offset: Long,
    val length: Long,
    val prefix: ByteArray = ByteArray(0)
)

class RpaReader(private val file: File) : AutoCloseable {
    private val raf = RandomAccessFile(file, "r")
    private var files: Map<String, RpaEntry> = emptyMap()
    private var version: String = "Unknown"
    
    init {
        parse()
    }

    private fun parse() {
        val headerRaw = readLineRaw()
        val headerStr = String(headerRaw, Charsets.UTF_8).trim()

        var indexOffset: Long = 0
        var key: Long? = null

        if (headerStr.startsWith("RPA-3.0")) {
            version = "RPA-3.0"
            val parts = headerStr.split(" ")
            indexOffset = parts[1].toLong(16) // Hex to Long
            key = parts[2].toLong(16)
        } else if (headerStr.startsWith("RPA-2.0")) {
            version = "RPA-2.0"
            val parts = headerStr.split(" ")
            indexOffset = parts[1].toLong(16)
        } else {
            // Assume RPA-1.0 or format without explicit textual header
            // In RPA-1.0 the file starts with the zlib stream
            version = "RPA-1.0"
            indexOffset = 0
            raf.seek(0)
        }

        readIndex(indexOffset, key)
    }
    
    // Reads a line of bytes until \n
    private fun readLineRaw(): ByteArray {
        raf.seek(0)
        val buffer = java.io.ByteArrayOutputStream()
        var b = raf.read()
        while (b != -1 && b.toChar() != '\n') {
            buffer.write(b)
            b = raf.read()
        }
        return buffer.toByteArray()
    }

    private fun readIndex(offset: Long, key: Long?) {
        raf.seek(offset)
        
        // Read the entire index from the offset to the end
        val rawIndexLength = raf.length() - offset
        val compressedData = ByteArray(rawIndexLength.toInt())
        raf.readFully(compressedData)

        // Decompress (zlib)
        val inflater = InflaterInputStream(ByteArrayInputStream(compressedData))
        
        // Deserialize (Pickle)
        val unpickler = Unpickler()
        // The result is a Map<String, List<Any>> where Any is a tuple or list
        val rawIndex = unpickler.load(inflater) as Map<String, Any>
        
        val entries = mutableMapOf<String, RpaEntry>()

        for ((pathRaw, entryDataRaw) in rawIndex) {
            val path = pathRaw.toString() 
            
            // entryDataRaw is a list of lists/tuples. Usually we take the first
            // Structure: [[offset, length, prefix], ...]
            // It can be a List or an Object[]
            val entryList: List<Any> = when (entryDataRaw) {
                is List<*> -> entryDataRaw as List<Any>
                is Array<*> -> entryDataRaw.toList() as List<Any>
                else -> continue
            }

            if (entryList.isNotEmpty()) {
                val firstPartRaw = entryList[0] 
                val firstPart: List<Any> = when (firstPartRaw) {
                    is List<*> -> firstPartRaw as List<Any>
                    is Array<*> -> firstPartRaw.toList() as List<Any>
                    else -> continue
                }
                
                // Extract raw data
                // Note: Python Pickle can map integers to Long, Int or BigInteger in Java...
                var rawOffset = (firstPart[0] as Number).toLong()
                var rawLength = (firstPart[1] as Number).toLong()
                var prefix = ByteArray(0)
                
                if (firstPart.size > 2) {
                    // Prefix
                    val prefixObj = firstPart[2]
                    if (prefixObj is ByteArray) {
                        prefix = prefixObj
                    } else if (prefixObj is String) {
                        prefix = prefixObj.toByteArray(Charsets.ISO_8859_1) // 'latin1' byte encoding
                    }
                }

                // De-obfuscate if there is a key (Only RPA-3.0)
                if (key != null) {
                    rawOffset = rawOffset xor key
                    rawLength = rawLength xor key
                }

                entries[path] = RpaEntry(path, rawOffset, rawLength, prefix)
            }
        }
        
        this.files = entries
    }

    fun getFileList(): List<String> {
        return files.keys.sorted()
    }
    
    fun getEntry(path: String): RpaEntry? {
        return files[path]
    }

    /**
    * Extracts a specific file to an OutputStream.
    */
    fun extractFile(path: String, outputStream: java.io.OutputStream) {
        val entry = files[path] ?: throw IOException("File not found: $path")
        
        // If there is a prefix, write it first
        if (entry.prefix.isNotEmpty()) {
            outputStream.write(entry.prefix)
        }

        // Read from the RPA file
        raf.seek(entry.offset)
        val buffer = ByteArray(8192) // 8KB buffer
        var remaining = entry.length
        
        while (remaining > 0) {
            val toRead = minOf(buffer.size.toLong(), remaining).toInt()
            val read = raf.read(buffer, 0, toRead)
            if (read == -1) break
            
            outputStream.write(buffer, 0, read)
            remaining -= read
        }
    }

    override fun close() {
        raf.close()
    }
}
