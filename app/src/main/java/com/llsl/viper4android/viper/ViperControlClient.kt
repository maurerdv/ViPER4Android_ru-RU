package com.llsl.viper4android.viper

import android.annotation.SuppressLint
import android.os.IBinder
import android.os.Parcel
import com.llsl.viper4android.utils.FileLogger

@SuppressLint("PrivateApi")
object ViperControlClient {
    private const val SERVICE_NAME = "viper.control"
    private const val DESCRIPTOR = "viper.fx.IViperControl"
    private const val TRANSACTION_DISPATCH_PARAM = IBinder.FIRST_CALL_TRANSACTION + 0
    private const val TRANSACTION_GET_STATUS = IBinder.FIRST_CALL_TRANSACTION + 1

    private val getServiceMethod by lazy {
        try {
            Class
                .forName("android.os.ServiceManager")
                .getMethod("getService", String::class.java)
        } catch (e: Exception) {
            FileLogger.e("CtrlClient", "ServiceManager.getService unavailable", e)
            null
        }
    }

    @Volatile
    private var cachedBinder: IBinder? = null

    private val deathRecipient =
        IBinder.DeathRecipient {
            FileLogger.w("CtrlClient", "viper.control binder died, clearing cache")
            cachedBinder = null
        }

    private fun lookupService(): IBinder? =
        try {
            (getServiceMethod?.invoke(null, SERVICE_NAME) as? IBinder).also {
                if (it == null) FileLogger.w("CtrlClient", "service($SERVICE_NAME) is null")
            }
        } catch (e: Exception) {
            FileLogger.e("CtrlClient", "getService($SERVICE_NAME) failed", e)
            null
        }

    private fun service(): IBinder? {
        cachedBinder?.let { return it }
        val binder = lookupService() ?: return null
        try {
            binder.linkToDeath(deathRecipient, 0)
        } catch (e: Exception) {
            FileLogger.w("CtrlClient", "linkToDeath failed: ${e.message}")
        }
        cachedBinder = binder
        return binder
    }

    private fun transact(
        code: Int,
        write: (Parcel) -> Unit,
    ): Boolean {
        val binder = service() ?: return false
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        return try {
            data.writeInterfaceToken(DESCRIPTOR)
            write(data)
            binder.transact(code, data, reply, 0)
            reply.readException()
            true
        } catch (e: Exception) {
            FileLogger.e("CtrlClient", "transact($code) failed", e)
            false
        } finally {
            data.recycle()
            reply.recycle()
        }
    }

    private fun dispatchBytes(
        param: Int,
        value: ByteArray,
    ): Boolean =
        transact(TRANSACTION_DISPATCH_PARAM) { p ->
            p.writeInt(param)
            p.writeByteArray(value)
        }

    fun dispatchParam(
        param: Int,
        value: Int,
    ): Boolean = dispatchBytes(param, ViperParamPayload.int(value))

    fun dispatchParam(
        param: Int,
        value: Boolean,
    ): Boolean = dispatchBytes(param, ViperParamPayload.bool(value))

    fun dispatchParam(
        param: Int,
        value: Float,
    ): Boolean = dispatchBytes(param, ViperParamPayload.float(value))

    fun dispatchParam(
        param: Int,
        index: Int,
        value: Int,
    ): Boolean = dispatchBytes(param, ViperParamPayload.int(value, index))

    fun dispatchParam(
        param: Int,
        index: Int,
        value: Boolean,
    ): Boolean = dispatchBytes(param, ViperParamPayload.bool(value, index))

    fun dispatchParam(
        param: Int,
        index: Int,
        value: Float,
    ): Boolean = dispatchBytes(param, ViperParamPayload.float(value, index))

    fun dispatchParam(
        param: Int,
        val1: Int,
        val2: Int,
        val3: Int,
    ): Boolean = dispatchBytes(param, ViperParamPayload.intArray(intArrayOf(val1, val2, val3)))

    fun dispatchParam(
        param: Int,
        value: ByteArray,
    ): Boolean = dispatchBytes(param, ViperParamPayload.bytes(value))

    fun dispatchParam(
        param: Int,
        value: FloatArray,
    ): Boolean = dispatchBytes(param, ViperParamPayload.floatArray(value))

    fun getStatus(): DriverStatus? {
        val binder = service() ?: return null
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        return try {
            data.writeInterfaceToken(DESCRIPTOR)
            binder.transact(TRANSACTION_GET_STATUS, data, reply, 0)
            reply.readException()
            val hasReply = reply.readInt()
            if (hasReply == 0) return null
            reply.readInt()
            DriverStatus(
                enabled = reply.readInt() != 0,
                sampleRate = reply.readInt(),
                processedFrames = reply.readLong(),
                kernelId = reply.readInt(),
                versionCode = reply.readInt(),
                versionName = reply.readString() ?: "",
                arch = reply.readString() ?: "",
            )
        } catch (e: Exception) {
            FileLogger.e("CtrlClient", "getStatus failed", e)
            null
        } finally {
            data.recycle()
            reply.recycle()
        }
    }
}
