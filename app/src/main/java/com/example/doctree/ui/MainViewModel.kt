package com.example.doctree.ui


import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.doctree.core.FileTreeGenerator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.File




class MainViewModel:ViewModel(){



    private val _state =
        MutableStateFlow(
            UiState()
        )


    val state:StateFlow<UiState> =
        _state



    fun generate(
        folder:File
    ){


        viewModelScope.launch {


            _state.value =
                _state.value.copy(
                    loading = true
                )



            val result =
                FileTreeGenerator.generate(
                    folder
                )



            _state.value =
                UiState(
                    treeText = result,
                    message = "生成完成"
                )


        }


    }



    fun updateText(
        text:String
    ){


        _state.value =
            _state.value.copy(
                treeText=text
            )


    }



    fun clear(){


        _state.value =
            UiState()


    }



}
