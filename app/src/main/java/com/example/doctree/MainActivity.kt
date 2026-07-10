package com.example.doctree



import android.os.Bundle
import android.widget.EditText
import android.widget.TextView
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.doctree.ui.MainViewModel
import com.example.doctree.utils.ClipboardUtils
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.launch



class MainActivity:
    AppCompatActivity(){



    private val viewModel:
            MainViewModel by viewModels()



    override fun onCreate(
        savedInstanceState:Bundle?
    ){
        super.onCreate(
            savedInstanceState
        )


        setContentView(
            R.layout.activity_main
        )



        val input =
            findViewById<EditText>(
                R.id.editTree
            )


        val output =
            findViewById<TextView>(
                R.id.textResult
            )


        val btnGenerate =
            findViewById<MaterialButton>(
                R.id.btnGenerate
            )


        val btnCopy =
            findViewById<MaterialButton>(
                R.id.btnCopy
            )



        lifecycleScope.launch {


            viewModel.state.collect {


                output.text =
                    if(it.treeText.isEmpty()){

                        "等待生成..."

                    }else{

                        it.treeText

                    }


            }


        }




        btnGenerate.setOnClickListener {


            val text =
                input.text.toString()


            if(text.isNotBlank()){


                viewModel.updateText(
                    text
                )


            }



        }




        btnCopy.setOnClickListener {


            ClipboardUtils.copy(
                this,
                output.text.toString()
            )


        }



    }


}
