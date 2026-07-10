package com.example.doctree.core


import java.io.File


object FileTreeGenerator {


    fun generate(
        root:File
    ):String{


        val builder =
            StringBuilder()


        build(
            root,
            "",
            builder
        )


        return builder.toString()

    }



    private fun build(
        file:File,
        prefix:String,
        builder:StringBuilder
    ){


        builder.append(prefix)
            .append(
                file.name
            )


        if(file.isDirectory){

            builder.append("/")

        }


        builder.append("\n")



        if(file.isDirectory){


            file.listFiles()
                ?.sortedBy {
                    it.name
                }
                ?.forEachIndexed { index,item ->


                    val next =
                        if(index ==
                            file.listFiles()!!.size-1
                        ){

                            prefix+"    "

                        }else{

                            prefix+"│   "

                        }



                    build(
                        item,
                        next,
                        builder
                    )

                }


        }

    }


}
