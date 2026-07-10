package com.example.doctree.utils


import android.content.ClipData
import android.content.Context
import android.content.ClipboardManager



object ClipboardUtils {


    fun copy(
        context:Context,
        text:String
    ){


        val manager =
            context.getSystemService(
                Context.CLIPBOARD_SERVICE
            ) as ClipboardManager



        manager.setPrimaryClip(
            ClipData.newPlainText(
                "tree",
                text
            )
        )


    }


}
