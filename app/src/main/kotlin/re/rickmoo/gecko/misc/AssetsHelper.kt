package re.rickmoo.gecko.misc

import android.content.Context
import android.util.Log
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

object AssetsHelper {

    /**
     * 将 Assets 中 CNENDictation 文件夹下的所有文件复制到目标文件夹
     *
     * @param context 上下文
     * @param destDir 目标文件夹 (File对象)
     * @return 是否全部复制成功
     */
    fun copyAssetsToDir(context: Context, assetsSubDir: String, destDir: File): Boolean {
        val assetManager = context.assets

        // list方法的参数是相对路径，不需要加 "/"
        val fileNames = assetManager.list(assetsSubDir.trim('/'))

        if (fileNames.isNullOrEmpty()) {
            // 文件夹为空或不存在
            return false
        }

        // 2. 确保目标文件夹存在
        if (!destDir.exists()) {
            val mkdirSuccess = destDir.mkdirs()
            if (!mkdirSuccess) return false
        }

        // 3. 遍历并复制文件
        for (fileName in fileNames) {
            // 拼接源文件在 assets 中的路径
            val assetPath = "$assetsSubDir/$fileName"

            try {
                // 创建目标文件对象
                val outFile = File(destDir, fileName)
                assetManager.open(assetPath).use { `in` -> outFile.outputStream().use { `in`.copyTo(it) } }
            } catch (e: IOException) {
                Log.e("AssetsHelper", "Error copying $assetsSubDir/$fileName", e)
                return false // 或者选择 continue 跳过出错的文件
            }
        }
        return true
    }

}

fun InputStream.copyTo(out: OutputStream) {
    val buffer = ByteArray(8192)
    var read: Int
    while ((this.read(buffer, 0, 8192).also { read = it }) >= 0) {
        out.write(buffer, 0, read)
    }
}