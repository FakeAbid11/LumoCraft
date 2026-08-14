package com.lumocraft.app.data.version

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure-JVM parsing tests for version JSON handling. org.json is provided
 * by the test-only dependency so no Android/Robolectric runtime is needed.
 */
class VersionJsonTest {

    private fun versionJson(libraries: JSONArray, extra: JSONObject = JSONObject()): JSONObject =
        JSONObject().put("libraries", libraries).apply {
            extra.keys().forEach { key -> put(key, extra.get(key)) }
        }

    @Test
    fun `libraries without rules are all included`() {
        val json = versionJson(
            JSONArray()
                .put(JSONObject().put("name", "a:b:1").put("path", "a/b/1.jar"))
                .put(JSONObject().put("name", "c:d:2").put("path", "c/d/2.jar"))
        )
        val libraries = VersionJson.libraries(json)
        assertEquals(2, libraries.size)
        assertEquals("a/b/1.jar", libraries[0].path)
        assertNull(libraries[0].classifier)
    }

    @Test
    fun `rules filter out incompatible libraries`() {
        val disallowed = JSONObject()
            .put("name", "win-only:1")
            .put("path", "win-only/1.jar")
            .put("rules", JSONArray().put(
                JSONObject().put("action", "disallow").put("os", JSONObject().put("name", "linux"))
            ))
        val json = versionJson(JSONArray().put(disallowed))
        assertTrue(VersionJson.libraries(json).isEmpty())
    }

    @Test
    fun `natives classifiers are resolved for the current os`() {
        val json = versionJson(
            JSONArray().put(
                JSONObject()
                    .put("name", "org.lwjgl:lwjgl:3.3.1")
                    .put("natives", JSONObject().put("linux", "natives-linux"))
                    .put("downloads", JSONObject().put("classifiers", JSONObject().put(
                        "natives-linux",
                        JSONObject()
                            .put("path", "org/lwjgl/lwjgl/3.3.1/lwjgl-3.3.1-natives-linux.jar")
                            .put("sha1", "abc123")
                            .put("size", 42L)
                    )))
            )
        )
        val library = VersionJson.libraries(json).single()
        assertEquals("natives-linux", library.classifier)
        assertEquals("org/lwjgl/lwjgl/3.3.1/lwjgl-3.3.1-natives-linux.jar", library.path)
        assertEquals("abc123", library.sha1)
        assertEquals(42L, library.size)
    }

    @Test
    fun `maven coordinates are resolved without download objects`() {
        val json = versionJson(
            JSONArray().put(
                JSONObject()
                    .put("name", "net.fabricmc:fabric-loader:0.15.11")
                    .put("url", "https://maven.fabricmc.net/")
            )
        )
        val library = VersionJson.libraries(json).single()
        assertEquals("net/fabricmc/fabric-loader/0.15.11/fabric-loader-0.15.11.jar", library.path)
        assertEquals("https://maven.fabricmc.net/net/fabricmc/fabric-loader/0.15.11/fabric-loader-0.15.11.jar", library.url)
    }

    @Test
    fun `maven coordinates with classifier keep the classifier in the filename`() {
        val json = versionJson(
            JSONArray().put(
                JSONObject()
                    .put("name", "org.lwjgl:lwjgl:3.3.1:natives-linux")
            )
        )
        val library = VersionJson.libraries(json).single()
        assertEquals("org/lwjgl/lwjgl/3.3.1/lwjgl-3.3.1-natives-linux.jar", library.path)
        assertEquals("natives-linux", library.classifier)
    }

    @Test
    fun `resolveArguments flattens rule-wrapped values`() {
        val array = JSONArray()
            .put("--width")
            .put(JSONObject().put("value", JSONArray().put("--height").put("480")))
            .put(JSONObject().put(
                "rules",
                JSONArray().put(JSONObject().put("action", "disallow").put("os", JSONObject().put("name", "linux")))
            ).put("value", "never-seen"))
        val args = VersionJson.resolveArguments(array)
        assertTrue(args.contains("--width"))
        assertTrue(args.contains("--height"))
        assertTrue(args.contains("480"))
        assertTrue("never-seen" !in args)
    }

    @Test
    fun `assetIndex and loggingConfig are parsed or null`() {
        val json = versionJson(
            JSONArray(),
            extra = JSONObject()
                .put("assetIndex", JSONObject().put("id", "1.21").put("url", "https://x/index.json").put("sha1", "s").put("size", 7L))
                .put("logging", JSONObject().put("client", JSONObject().put("file", JSONObject().put("id", "logcfg.json"))))
        )
        val index = VersionJson.assetIndex(json)
        assertEquals("1.21", index?.id)
        assertEquals("s", index?.sha1)
        val logging = VersionJson.loggingConfig(json)
        assertEquals("logcfg.json", logging?.id)
        assertNull(VersionJson.assetIndex(JSONObject()))
        assertNull(VersionJson.loggingConfig(JSONObject()))
    }
}