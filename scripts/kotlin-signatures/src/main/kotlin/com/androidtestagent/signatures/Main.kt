package com.androidtestagent.signatures

fun main(args: Array<String>) {
    if (args.isEmpty()) {
        System.err.println("usage: kotlin-signatures <path-to-kotlin-file>")
        kotlin.system.exitProcess(2)
    }
    val input = java.io.File(args[0])
    if (!input.exists()) {
        System.err.println("error: file not found: ${args[0]}")
        kotlin.system.exitProcess(1)
    }
    val source = input.readText()
    val output = stripBodies(source)
    print(output)
}

fun stripBodies(source: String): String {
    // Placeholder: implemented across later tasks.
    return source
}
