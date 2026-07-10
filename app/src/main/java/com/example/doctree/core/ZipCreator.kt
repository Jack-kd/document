package com.example.doctree.core


import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream



object ZipCreator {


    fun create(
        source:File,
        target:File
    ){


        ZipOutputStream(
            FileOutputStream(target)
        ).use { zip ->


            zipFolder(
                source,
                source.name,
                zip
            )


        }


    }



    private fun zipFolder(
        file:File,
        path:String,
        zip:ZipOutputStream
    ){


        if(file.isDirectory){


            file.listFiles()
                ?.forEach {


                    zipFolder(
                        it,
                        "$path/${it.name}",
                        zip
                    )


                }


        }else{


            val entry =
                ZipEntry(path)


            zip.putNextEntry(
                entry
            )


            file.inputStream()
                .use { input ->


                    input.copyTo(
                        zip
                    )


                }


            zip.closeEntry()


        }


    }


}
