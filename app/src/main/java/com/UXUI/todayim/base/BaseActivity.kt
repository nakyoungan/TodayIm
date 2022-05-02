package com.UXUI.todayim.base

import android.view.View
import androidx.appcompat.app.AppCompatActivity

open class BaseActivity: AppCompatActivity(), View.OnClickListener, BaseDialog.BaseDialogClickListener {
    override fun onClick(v: View?) {

    }

    override fun onOKClicked() {

    }

    override fun onCancelClicked() {

    }

    fun showDialog(title: String, message: String , okMessage: String) {
        val dig = BaseDialog(this)
        dig.listener = this
        dig.show(title, message, okMessage)
    }

}