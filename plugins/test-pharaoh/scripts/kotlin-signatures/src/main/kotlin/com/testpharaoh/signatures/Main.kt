package com.testpharaoh.signatures

internal class ExitException(
    val code: Int,
) : RuntimeException()

fun main(args: Array<String>) {
    try {
        run(args)
    } catch (e: ExitException) {
        kotlin.system.exitProcess(e.code)
    }
}

internal fun run(args: Array<String>) {
    if (args.isEmpty()) {
        System.err.println("usage: kotlin-signatures <path-to-kotlin-file>")
        throw ExitException(2)
    }
    val input = java.io.File(args[0])
    if (!input.exists()) {
        System.err.println("error: file not found: ${args[0]}")
        throw ExitException(1)
    }
    val source = input.readText()
    val output = stripBodies(source)
    print(output)
}
