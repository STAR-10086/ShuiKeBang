package com.star.shuikebang.asr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ArchiveSafetyTest {

    private val root = File(System.getProperty("java.io.tmpdir"), "archive-safety-test").apply {
        deleteRecursively(); mkdirs()
    }

    private fun canonicalInside(out: File) {
        val rc = root.canonicalFile
        val oc = out.canonicalFile
        assertTrue("${oc.path} 必须在 ${rc.path} 内", oc.path.startsWith(rc.path + File.separator))
    }

    @Test
    fun normal_entries_stay_inside() {
        canonicalInside(ArchiveSafety.safeResolve(root, "encoder.onnx"))
        canonicalInside(ArchiveSafety.safeResolve(root, "sub/dir/decoder.onnx"))
        // 前导斜杠被去掉，按相对路径处理，仍在目录内
        val abs = ArchiveSafety.safeResolve(root, "/tokens.txt")
        canonicalInside(abs)
        assertEquals("tokens.txt", abs.name)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejects_parent_traversal() {
        ArchiveSafety.safeResolve(root, "../evil.onnx")
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejects_deep_traversal() {
        ArchiveSafety.safeResolve(root, "a/../../etc/passwd")
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejects_backslash_traversal() {
        ArchiveSafety.safeResolve(root, "..\\..\\windows\\system32")
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejects_empty_name() {
        ArchiveSafety.safeResolve(root, "   ")
    }
}
