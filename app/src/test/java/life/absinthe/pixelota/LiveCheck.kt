package life.absinthe.pixelota

import java.io.File

/** Optional network smoke check, separate from deterministic unit tests. */
object LiveCheck {
    @JvmStatic
    fun main(args: Array<String>) {
        val result = OtaRepository().query(args[0], args[1], File(args[2]))
        println("current=${result.current?.url ?: "not found"}")
        println("latest=${result.latest?.url ?: "not found"}")
        println("betaComplete=${result.betaComplete}")
        for (warning in result.warnings) {
            println("warning=$warning")
        }
        if (result.latest == null || !result.betaComplete || result.current == null) {
            throw AssertionError("Live source check incomplete")
        }
    }
}
