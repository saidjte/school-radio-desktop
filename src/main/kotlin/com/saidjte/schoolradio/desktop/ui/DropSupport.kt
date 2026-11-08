package com.saidjte.schoolradio.desktop.ui

import com.saidjte.schoolradio.desktop.data.DropLogic
import java.awt.datatransfer.DataFlavor
import java.awt.dnd.DnDConstants
import java.awt.dnd.DropTarget
import java.awt.dnd.DropTargetAdapter
import java.awt.dnd.DropTargetDragEvent
import java.awt.dnd.DropTargetDropEvent
import java.awt.dnd.DropTargetEvent
import java.io.File

/**
 * جسر سحب وإفلات ملفات نظام التشغيل (v0.39.0 «سحب وإفلات الملفات الصوتية») —
 * DropTarget من AWT الخالص (جزء من JDK فلا ثنائيات خارجية ولا شبكة) يُثبَّت
 * على نافذة التطبيق فيقبل الإفلات من مدير الملفات على ويندوز ولينكس وماك.
 *
 * القرار النقي (أي ملفات تُقبل) في [DropLogic] المُختبَر عقدًا، وهنا حراسة
 * AWT فقط: نعلن القبول حين يحمل السحب قائمة ملفات، ونستلمها عند الإفلات
 * ونمرّرها للمسار المعالج في AppViewModel.
 */
object DropSupport {

    /** هل يحمل حدث السحب قائمة ملفات من نظام التشغيل؟ */
    fun carriesFileList(flavors: Array<DataFlavor>): Boolean =
        flavors.any { it.isFlavorJavaFileListType() }

    /** استخراج ملفات حدث الإفلات — فشل القراءة يعود بقائمة فارغة لا استثناء. */
    @Suppress("UNCHECKED_CAST")
    fun filesOf(ev: DropTargetDropEvent): List<File> = try {
        val t = ev.transferable
        if (t.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
            (t.getTransferData(DataFlavor.javaFileListFlavor) as? List<File>) ?: emptyList()
        } else emptyList()
    } catch (_: Exception) {
        emptyList()
    }

    /**
     * تثبيت هدف الإفلات على مكوّن AWT (نافذة Compose هي Frame):
     * @param onHover حالة السحب فوق النافذة — للإبراز الحي في الواجهة
     * @param onFiles تُستدعى مرة واحدة عند الإفلات الفعلي بكل الملفات
     */
    fun install(
        component: java.awt.Component,
        onHover: (Boolean) -> Unit,
        onFiles: (List<File>) -> Unit
    ) {
        val target = object : DropTarget() {
            override fun dragEnter(ev: DropTargetDragEvent) {
                if (carriesFileList(ev.currentDataFlavors)) {
                    ev.acceptDrag(DnDConstants.ACTION_COPY)
                    onHover(true)
                } else {
                    ev.rejectDrag()
                }
            }

            override fun dragOver(ev: DropTargetDragEvent) {
                if (carriesFileList(ev.currentDataFlavors)) {
                    ev.acceptDrag(DnDConstants.ACTION_COPY)
                } else {
                    ev.rejectDrag()
                }
            }

            override fun dragExit(ev: DropTargetEvent) {
                onHover(false)
            }

            override fun drop(ev: DropTargetDropEvent) {
                onHover(false)
                if (!carriesFileList(ev.currentDataFlavors)) {
                    ev.rejectDrop()
                    return
                }
                ev.acceptDrop(DnDConstants.ACTION_COPY)
                val files = filesOf(ev)
                ev.dropComplete(files.isNotEmpty())
                if (files.isNotEmpty()) onFiles(files)
            }
        }
        component.dropTarget = target
    }
}
