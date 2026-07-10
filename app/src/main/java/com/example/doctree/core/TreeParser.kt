package com.example.doctree.core


object TreeParser {


    fun normalize(
        text:String
    ):List<String>{


        val result =
            mutableListOf<String>()


        text.lines()
            .filter {
                it.isNotBlank()
            }
            .forEach {


                var line =
                    it.trim()


                line =
                    line.replace(
                        "├──",
                        ""
                    )
                    .replace(
                        "└──",
                        ""
                    )
                    .replace(
                        "│",
                        ""
                    )
                    .trim()



                if(line.isNotEmpty()){

                    result.add(line)

                }

            }


        return result

    }



}
