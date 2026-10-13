package com.flashsms.gateway

import android.Manifest
import android.app.PendingIntent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.telephony.SmsManager
import android.telephony.SmsMessage
import android.text.InputType
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.io.DataOutputStream

class MainActivity : AppCompatActivity() {

    private lateinit var etPhone: EditText
    private lateinit var etMessage: EditText
    private lateinit var etModemPort: EditText
    private lateinit var rgMethod: RadioGroup
    private lateinit var tvLog: TextView

    companion object {
        private const val ID_METHOD_PDU = 1001
        private const val ID_METHOD_AT = 1002
        private const val ID_METHOD_NORMAL = 1003
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupUI()
        checkPermissions()
    }

    private fun setupUI() {
        val scroll = ScrollView(this)
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 48, 48, 48)
        }

        val tvTitle = TextView(this).apply {
            text = "Disparador Flash SMS (Classe 0)"
            textSize = 20f
            setPadding(0, 0, 0, 32)
        }

        etPhone = EditText(this).apply {
            hint = "Número (Ex: +5547999999999)"
            inputType = InputType.TYPE_CLASS_PHONE
        }

        etMessage = EditText(this).apply {
            hint = "Mensagem do Flash SMS (Sem acentos para 7-bit)"
            minLines = 3
        }

        val tvMethodLabel = TextView(this).apply {
            text = "\nMétodo de Disparo:"
            textSize = 16f
        }

        rgMethod = RadioGroup(this).apply {
            val rbPdu = RadioButton(this@MainActivity).apply {
                id = ID_METHOD_PDU
                text = "1. PDU Classe 0 (DCS=0x10 via Reflexão)"
                isChecked = true
            }
            val rbAt = RadioButton(this@MainActivity).apply {
                id = ID_METHOD_AT
                text = "2. Comandos AT no Modem (Requer Root)"
            }
            val rbNormal = RadioButton(this@MainActivity).apply {
                id = ID_METHOD_NORMAL
                text = "3. SMS Comum Classe 1 (Teste de API)"
            }
            addView(rbPdu)
            addView(rbAt)
            addView(rbNormal)
        }

        etModemPort = EditText(this).apply {
            hint = "Porta do Modem (apenas Método 2)"
            setText("/dev/smd7")
        }

        val btnSend = Button(this).apply {
            text = "ENVIAR AGORA"
            setOnClickListener { dispararMensagem() }
        }

        tvLog = TextView(this).apply {
            text = "\nLog de Execução:\nPronto."
            setPadding(0, 32, 0, 0)
            setTextIsSelectable(true)
        }

        layout.addView(tvTitle)
        layout.addView(etPhone)
        layout.addView(etMessage)
        layout.addView(tvMethodLabel)
        layout.addView(rgMethod)
        layout.addView(etModemPort)
        layout.addView(btnSend)
        layout.addView(tvLog)
        scroll.addView(layout)
        setContentView(scroll)
    }

    private fun checkPermissions() {
        val perms = arrayOf(Manifest.permission.SEND_SMS, Manifest.permission.READ_PHONE_STATE)
        val missing = perms.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, missing.toTypedArray(), 101)
        }
    }

    private fun log(msg: String) {
        runOnUiThread {
            tvLog.append("\n• $msg")
        }
    }

    private fun dispararMensagem() {
        val phone = etPhone.text.toString().trim()
        val msg = etMessage.text.toString().trim()

        if (phone.isEmpty() || msg.isEmpty()) {
            Toast.makeText(this, "Preencha número e mensagem!", Toast.LENGTH_SHORT).show()
            return
        }

        tvLog.text = "Log de Execução:"
        when (rgMethod.checkedRadioButtonId) {
            ID_METHOD_PDU -> enviarFlashViaPdu(phone, msg)
            ID_METHOD_AT -> enviarFlashViaComandosAT(phone, msg, etModemPort.text.toString().trim())
            ID_METHOD_NORMAL -> enviarSmsPadrao(phone, msg)
        }
    }

    @Suppress("DEPRECATION")
    private fun enviarFlashViaPdu(phone: String, message: String) {
        try {
            val submitPdu = SmsMessage.getSubmitPdu(null, phone, message, false)
            if (submitPdu == null || submitPdu.encodedMessage == null) {
                log("Erro: Não foi possível gerar a PDU para os dados informados.")
                return
            }
            val rawPdu = submitPdu.encodedMessage

            val destLenDigits = rawPdu[2].toInt() and 0xFF
            val destOctets = (destLenDigits + 1) / 2
            val offsetPid = 4 + destOctets
            val offsetDcs = offsetPid + 1

            val dcsOriginal = rawPdu[offsetDcs]
            rawPdu[offsetDcs] = (dcsOriginal.toInt() or 0x10).toByte()

            val pduHex = rawPdu.joinToString("") { "%02X".format(it) }
            log("PDU Classe 0 gerada (DCS alterado de 0x%02X para 0x%02X):".format(dcsOriginal, rawPdu[offsetDcs]))
            log("HEX: $pduHex")

            val smsManager = getSmsManagerInstance()
            val method = smsManager.javaClass.getDeclaredMethod(
                "sendRawPdu",
                ByteArray::class.java,
                ByteArray::class.java,
                PendingIntent::class.java,
                PendingIntent::class.java
            )
            method.isAccessible = true
            method.invoke(smsManager, submitPdu.encodedScAddress, rawPdu, null, null)
            log("Sucesso: PDU Classe 0 despachada via sendRawPdu!")

        } catch (e: NoSuchMethodException) {
            log("Bloqueio do Android: 'sendRawPdu' ocultado nesta versão do Android sem Root.")
            log("Dica: Use o Método 2 (Comandos AT via Root) ou copie a PDU HEX gerada acima.")
        } catch (e: Exception) {
            log("Erro ao enviar PDU: ${e.javaClass.simpleName} - ${e.message}")
        }
    }

    private fun enviarFlashViaComandosAT(phone: String, message: String, port: String) {
        Thread {
            try {
                log("Solicitando acesso Root (su) para porta $port...")
                val process = Runtime.getRuntime().exec("su")
                val os = DataOutputStream(process.outputStream)

                val cmds = listOf(
                    "echo -e 'AT+CMGF=1\\r' > $port",
                    "sleep 1",
                    "echo -e 'AT+CSMP=17,167,0,16\\r' > $port",
                    "sleep 1",
                    "echo -e 'AT+CMGS=\"$phone\"\\r' > $port",
                    "sleep 1",
                    "echo -e '$message\\x1A' > $port",
                    "sleep 2",
                    "echo -e 'AT+CSMP=17,167,0,0\\r' > $port",
                    "exit"
                )

                for (cmd in cmds) {
                    os.writeBytes("$cmd\n")
                    os.flush()
                }

                val exitCode = process.waitFor()
                if (exitCode == 0) {
                    log("Comandos AT enviados com sucesso para $port (DCS=16)!")
                } else {
                    log("Falha ao executar como Root (Código: $exitCode). Verifique o Magisk/KernelSU.")
                }
            } catch (e: Exception) {
                log("Erro no modo AT/Root: ${e.message}")
            }
        }.start()
    }

    private fun enviarSmsPadrao(phone: String, message: String) {
        try {
            val smsManager = getSmsManagerInstance()
            smsManager.sendTextMessage(phone, null, message, null, null)
            log("SMS padrão (Classe 1) enviado com sucesso para $phone!")
        } catch (e: Exception) {
            log("Erro no envio padrão: ${e.message}")
        }
    }

    @Suppress("DEPRECATION")
    private fun getSmsManagerInstance(): SmsManager {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            this.getSystemService(SmsManager::class.java) ?: SmsManager.getDefault()
        } else {
            SmsManager.getDefault()
        }
    }
}