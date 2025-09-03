package com.ubase.uclass.network.response

class ErrorData(code: Int, msg: String?) {
    private var code = 0
    private var msg: String? = null


    fun getCode(): Int {
        return this.code
    }

    fun setCode(code: Int) {
        this.code = code
    }

    fun getMsg(): String? {
        return this.msg
    }

    fun setMsg(msg: String?) {
        this.msg = msg
    }
}