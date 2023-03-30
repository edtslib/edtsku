package id.co.edtslib.data

interface ProcessResultDelegate<T> {
    fun loading()
    fun error(code: String?, message: String?, data: T? = null)
    fun unAuthorize(message: String?)
    fun success(data: T?)
    fun errorConnection()
    fun errorSystem()
}