# Kotlin stdlib — Text & I/O

## String Operations

```kotlin
val s = "  Hello, World!  "

// Trim
s.trim()                    // "Hello, World!"
s.trimStart()               // "Hello, World!  "
s.trimEnd()                 // "  Hello, World!"
s.trimIndent()              // removes common leading whitespace (multi-line)
s.trimMargin("||")            // removes margin prefix

// Case
s.uppercase()               // "  HELLO, WORLD!  "
s.lowercase()               // "  hello, world!  "
s.replaceFirstChar { it.uppercase() }
s.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
s.capitalize()              // deprecated; use replaceFirstChar
s.decapitalize()            // deprecated; use replaceFirstChar

// Check
s.startsWith("  H")
s.endsWith("!  ")
s.contains("World")
s.contains("world", ignoreCase = true)
s.isEmpty() / .isNotEmpty()
s.isBlank() / .isNotBlank()  // blank = only whitespace
s.compareTo(other)            // lexicographic

// Search
s.indexOf("World")
s.indexOfFirst { it == 'W' }
s.indexOfLast { it == '!' }
s.lastIndexOf("o")
s.lastIndexOf("o", 5)       // limit search to index 5
s.lines()                     // List<String>
s.lineSequence()             // Sequence<String>

// Split
s.split(",")
s.split(",", limit = 2)
s.split(Regex("\\s+"))
s.splitToSequence(Regex("\\s+"))

// Replace
s.replace("World", "Kotlin")
s.replace(Regex("[^a-zA-Z]"), "")
s.replaceFirst("World", "Kotlin")
s.replaceFirst(Regex("[a-z]+"), "Hi")
s.replaceAfter("W", "Everyone")
s.replaceBefore("W", "Goodbye")
s.replaceIndent("  ")
s.replaceIndentToMatch("  ")

// Substring
s.substring(2, 7)             // "Hello"
s.substringAfter(",")
s.substringBefore(",")
s.substringAfterLast(",")
s.substringBeforeLast(",")
s.substringAfter("!")        // "" (not found)
s.substringAfter("!", missingDelimiterValue = "default")

// Remove
s.removePrefix("  ")
s.removeSuffix("!  ")
s.removeRange(0, 5)
s.removeSurrounding("<", ">")

// Extend / pad
"ab".repeat(3)               // "ababab"
"hi".padEnd(5, '-')          // "hi---"
"hi".padStart(5, '-')        // "---hi"
"hi".center(6, '-')          // "---hi-"

// Take / drop
s.take(5)
s.takeLast(3)
s.drop(2)
s.dropLast(2)
s.takeWhile { it != ',' }
s.dropWhile { it != 'W' }
s.takeIf { it.isNotEmpty() }
s.takeUnless { it.isEmpty() }
```

## Regex

```kotlin
// Create
val regex = Regex("^[a-zA-Z]+@[a-zA-Z]+\\.[a-zA-Z]+$")
val pattern = Regex("[0-9]+")

// Match
regex.matches("test@example.com")
regex.containsMatchIn("Email: test@x.com")

// Find
regex.find("test@example.com")              // MatchResult?
regex.findAll("a@b.com c@d.com")             // Sequence<MatchResult>
regex.findAnyOf(listOf("a", "b"))?           // (index, match)
regex.findAll("a@example.com b@test.com")

// Destructuring
val match = regex.find(input)
val (local, domain) = match?.destructured ?: return
// Named groups
val named = Regex("(?<user>\\w+)@(?<domain>\\w+\\.\\w+)")
val result = named.find("test@example.com")
val user = result?.groups?.get("user")?.value

// Replace
"123-456".replace(Regex("[^0-9]"), "")
"hello world".replace(Regex("(\\w+) (\\w+)")) { 
    "${it.groupValues[2]} ${it.groupValues[1]}" 
}
"hello".replace(Regex(".*")) { it.value.uppercase() }

// Split
"1, 2, 3".split(Regex("\\s*,\\s*"))
```

## HexFormat (Kotlin 1.9+)

```kotlin
import kotlin.text.HexFormat

// Format numbers
255.toHexString()                                 // "ff"
255.toHexString(HexFormat.Default)
255L.toHexString()
(-1).toHexString()                                  // "ff"

// Format with custom format
val format = HexFormat {
    upperCase = true
    prefix = "0x"
    suffix = ";"
    separator = ", "
}
255.toHexString(format)                             // "0XFF;"
byteArrayOf(0x12, 0x34).toHexString(format)         // "0x12;, 0x34;"

// Parse hex strings
"ff".hexToInt()                                    // 255
"0xff".hexToInt(HexFormat { prefix = "0x" })
"ff".hexToLong()
"ff".hexToULong()
"ff".hexToByte()                                   // -1 (signed)
"7f".hexToByte()                                   // 127
"ff".hexToUByte()
"ff".hexToShort()
"ffff".hexToUShort()
"ff".hexToByteArray()
"0x12, 0x34".hexToByteArray(HexFormat { 
    prefix = "0x"
    separator = ", "
})

// HexFormat configuration
HexFormat {
    upperCase = false
    prefix = ""
    suffix = ""
    separator = ""
    removeLeadingZeroHexDigits = false
    numberOfLeadingZeroHexDigits = 1  // for Byte
}
```

## I/O — kotlin.io

### File I/O (JVM)

```kotlin
import java.io.File
import java.io.BufferedReader

// Read
val text = File("data.txt").readText()
val lines = File("data.txt").readLines()
val lineSeq = File("data.txt").useLines { it.toList() }
val bytes = File("data.bin").readBytes()
val reader = File("data.txt").bufferedReader()

// Write
File("output.txt").writeText("Hello, Kotlin!")
File("output.txt").appendText("\nAppended")
File("data.bin").writeBytes(byteArray)
File("output.txt").printWriter().use { it.println("Hello") }

// Auto-close pattern
File("data.txt").bufferedReader().use { reader ->
    reader.lineSequence().forEach { println(it) }
}

File("output.txt").bufferedWriter().use { writer ->
    writer.write("Hello")
}

// Directory walking
File(".").walk()
    .maxDepth(3)
    .filter { it.extension == "kt" }
    .forEach { println(it.path) }

File(".").walkTopDown()
File(".").walkFileTree { file, attrs ->
    // visit file or directory
}

// Check existence
File("data.txt").exists()
File("data.txt").isFile
File("data.txt").isDirectory
File("data.txt").canRead()
File("data.txt").canWrite()
```

### kotlin.io.path (JVM NIO)

```kotlin
import java.nio.file.Path
import kotlin.io.path.*
import java.nio.file.StandardCopyOption

val path = Path("data.txt")

// Read/write
path.readText()
path.writeText("Hello")
path.readBytes()
path.writeBytes(byteArray)

// Append
path.appendText("More")
path.appendLines(listOf("a", "b"))

// Lines
path.useLines { lines -> lines.forEach { println(it) } }

// Copy/move/delete
path.copyTo(Path("backup.txt"))
path.copyTo(Path("backup.txt"), StandardCopyOption.REPLACE_EXISTING)
path.copyToRecursively(Path("dir"), StandardCopyOption.REPLACE_EXISTING)
path.moveTo(Path("moved.txt"))
path.deleteIfExists()
path.deleteRecursively()

// File operations
path.exists()
path.isReadable()
path.isWritable()
path.isHidden()
path.fileSize
path.extension
path.name
```

### kotlin.io.encoding (Kotlin 2.0+)

```kotlin
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

@OptIn(ExperimentalEncodingApi::class)
val encoded = Base64.encode("Hello".encodeToByteArray())
@OptIn(ExperimentalEncodingApi::class)
val decoded = Base64.decode(encoded).decodeToString()
```

### Multiplatform I/O

```kotlin
// readln (replaces deprecated readLine)
val name = readln()
val age = readln().toInt()

// print/println
print("no newline")
println("newline")
```