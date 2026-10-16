package org.jormungandr.dataframe.filetype

import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.vfs.VirtualFile
import org.jormungandr.dataframe.icon.DataFrameIcons
import javax.swing.Icon

/**
 * File type for Apache Arrow IPC (.arrow, .feather, .ipc) columnar streaming dataset files.
 */
class ArrowFileType private constructor() : FileType {

    override fun getName(): String = "Arrow IPC Dataset"

    override fun getDescription(): String = "Apache Arrow IPC Columnar Streaming Dataset"

    override fun getDefaultExtension(): String = "arrow"

    override fun getIcon(): Icon = DataFrameIcons.DATA_TABLE

    override fun isBinary(): Boolean = true

    override fun isReadOnly(): Boolean = false

    override fun getCharset(file: VirtualFile, content: ByteArray): String? = null

    companion object {
        @JvmField
        val INSTANCE = ArrowFileType()
    }
}
