package com.nudge.drive.desktop.ui

import com.nudge.drive.desktop.data.Platform
import java.awt.FileDialog
import java.awt.Frame
import java.awt.Window
import java.io.File
import javax.swing.JFileChooser
import javax.swing.UIManager

/** Native "Open" dialog (several files). */
fun pickFiles(owner: Window?, title: String): List<File> {
    val dialog = FileDialog(owner as? Frame, title, FileDialog.LOAD).apply { isMultipleMode = true }
    dialog.isVisible = true
    return dialog.files?.toList().orEmpty()
}

/** Folder picker (Finder's folder mode on macOS, the system-styled chooser on Windows). */
fun pickFolder(owner: Window?, title: String): File? {
    if (Platform.isMac) {
        System.setProperty("apple.awt.fileDialogForDirectories", "true")
        try {
            val d = FileDialog(owner as? Frame, title, FileDialog.LOAD); d.isVisible = true
            return d.file?.let { File(d.directory, it) }
        } finally { System.setProperty("apple.awt.fileDialogForDirectories", "false") }
    }
    runCatching { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()) }
    val chooser = JFileChooser().apply {
        dialogTitle = title
        fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
        isAcceptAllFileFilterUsed = false
    }
    return if (chooser.showOpenDialog(owner) == JFileChooser.APPROVE_OPTION) chooser.selectedFile else null
}

/** Puts files on the clipboard, so Ctrl+V pastes them into Explorer / Finder, chat apps or email. */
fun copyFiles(files: List<File>) {
    val t = object : java.awt.datatransfer.Transferable {
        override fun getTransferDataFlavors() = arrayOf(java.awt.datatransfer.DataFlavor.javaFileListFlavor)
        override fun isDataFlavorSupported(f: java.awt.datatransfer.DataFlavor) = f == java.awt.datatransfer.DataFlavor.javaFileListFlavor
        override fun getTransferData(f: java.awt.datatransfer.DataFlavor): Any {
            if (!isDataFlavorSupported(f)) throw java.awt.datatransfer.UnsupportedFlavorException(f)
            return files
        }
    }
    java.awt.Toolkit.getDefaultToolkit().systemClipboard.setContents(t, null)
}

/** Copies text to the clipboard. */
fun copyText(text: String) {
    java.awt.Toolkit.getDefaultToolkit().systemClipboard.setContents(java.awt.datatransfer.StringSelection(text), null)
}
