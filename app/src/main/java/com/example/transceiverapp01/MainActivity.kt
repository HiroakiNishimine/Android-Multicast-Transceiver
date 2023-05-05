// UDPマルチキャストを利用した「オーディオを送受信する」トランシーバーアプリ
// このコードは簡単な例であり、実際のアプリケーションではエラー処理、例外処理、リソースの開放、バックグラウンド処理、音声圧縮などの改善が必要になるでしょう。
// また、マルチキャスト通信に関連するパーミッション（INTERNETとACCESS_NETWORK_STATE）および音声録音と再生に関連するパーミッション（RECORD_AUDIOとMODIFY_AUDIO_SETTINGS）がAndroidマニフェストファイルに追加されていることを確認してください。

package com.example.transceiverapp01

import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.os.Bundle
import android.os.StrictMode
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import java.net.DatagramPacket
import java.net.InetAddress
import java.net.MulticastSocket
import android.util.Log
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import android.Manifest
import android.view.KeyEvent
import android.view.MotionEvent
import android.widget.TextView

class MainActivity : AppCompatActivity() {
    private val multicastGroupAddress = "224.0.0.1" // マルチキャストグループのアドレス
    private val multicastPort = 12345               // マルチキャスト用のポート番号
    private val sampleRate = 44100                  // サンプリングレート
    private val channelConfig = AudioFormat.CHANNEL_IN_MONO // チャンネル設定
    private val audioEncoding = AudioFormat.ENCODING_PCM_16BIT // オーディオエンコーディング
    private var isRecording = false                 // 録音中かどうかを示すフラグ
    private lateinit var messageTextView: TextView  // 画面上のメッセージテキストビュー

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // ユーザーがRECORD_AUDIOパーミッションを承認しているかどうかをチェック
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            // 実行時にRECORD_AUDIOパーミッションを要求
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), 1)
        } else {
            // すでにパーミッションが許可されている場合、送受信処理を開始
            setupAudioTransceiver()
        }

        // すべてのスレッドポリシーを許可する設定
        val policy = StrictMode.ThreadPolicy.Builder().permitAll().build()
        StrictMode.setThreadPolicy(policy)

        messageTextView = findViewById(R.id.messageTextView)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        Log.d("KeyCode", "Key pressed: $keyCode")
        // ここで、適切なキーコードに基づいて処理を行います。
        return super.onKeyDown(keyCode, event)
    }

    // パーミッション要求の結果を処理するメソッド
    // ユーザーがパーミッションを許可または拒否すると、OSに呼び出され、その結果を処理する
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            1 -> {
                // ユーザーがRECORD_AUDIOパーミッションを許可した場合、送受信処理を開始
                if ((grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
                    setupAudioTransceiver()
                } else {
                    // ユーザーがパーミッションを拒否した場合、アプリを終了
                    finish()
                }
                return
            }
            // 他のパーミッション要求に対しても適切に対処することができます。
            else -> {
                // 無視する
            }
        }
    }

    // 送信ボタンが長押しされたときに録音を開始し、ボタンがクリックされたときに録音を停止するように設定するメソッド（最後に受信処理を開始する）
    private fun setupAudioTransceiver() {
        // 送信ボタンを取得する
        val sendButton: Button = findViewById(R.id.send_button)

        // 送信ボタンが長押しされた場合の処理
        sendButton.setOnLongClickListener {
            // 録音中フラグを真に設定
            isRecording = true
            // 録音を開始する
            startRecording()
            // 録音開始のログを出力
            Log.i("TransceiverApp", "Started Recording")
            // イベントが処理されたことを示すため、trueを返す
            true
        }

        // 送信ボタンが押された場合の処理
        sendButton.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    // 録音中フラグを真に設定
                    isRecording = true
                    // 録音を開始する
                    startRecording()
                    // 録音開始のログを出力
                    Log.i("TransceiverApp", "Started Recording")
                    // イベントが処理されたことを示すため、trueを返す
                    true
                }
                MotionEvent.ACTION_UP -> {
                    // 録音中の場合
                    if (isRecording) {
                        // 録音中フラグを偽に設定
                        isRecording = false
                        // 録音停止のログを出力
                        Log.i("TransceiverApp", "Stopped Recording")
                    }
                    // イベントが処理されたことを示すため、trueを返す
                    true
                }
                else -> {
                    false
                }
            }
        }

        // 受信処理を開始する
        startReceivingAudio()
    }


    // 音声を録音して送信するメソッド
    private fun startRecording() {
        // 新しいスレッドを作成
        val thread = Thread {
            try {
                // 音声録音用のAudioRecordオブジェクトを作成
                val bufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioEncoding)
                val audioRecord: AudioRecord
                try {
                    audioRecord = AudioRecord(MediaRecorder.AudioSource.MIC, sampleRate, channelConfig, audioEncoding, bufferSize)
                } catch (e: SecurityException) {
                    Log.e("TransceiverApp", "Permission for microphone was not granted.", e)
                    return@Thread
                }
                // 音声録音を開始
                audioRecord.startRecording()
                // 録音中の音声データを格納するバッファを作成（1024バイト）
                val buffer = ByteArray(1024)
                // isRecordingフラグがtrueの間、音声を録音し続ける
                while (isRecording) {
                    // 音声データを読み取る
                    val bytesRead = audioRecord.read(buffer, 0, buffer.size)

                    // 音声データを送信する
                    if (bytesRead > 0) {
                        sendData(buffer, bytesRead)
                    }
                }

                // 音声録音を停止
                audioRecord.stop()
                audioRecord.release()

            } catch (e: Exception) {
                Log.i("TransceiverApp", "Exception Occurred! In Recording")
                // 例外が発生した場合、スタックトレースを出力する
                e.printStackTrace()
            }
        }
        // スレッドを開始する
        thread.start()
    }

    // 音声データを送信するメソッド
    private fun sendData(data: ByteArray, size: Int) {
        // 新しいスレッドを作成
        val thread = Thread {
            try {
                // マルチキャストソケットを作成する
                val multicastSocket = MulticastSocket()
                // マルチキャストグループのアドレスを取得する
                val multicastGroup = InetAddress.getByName(multicastGroupAddress)
                // データを送信するためのDatagramPacketオブジェクトを作成
                val datagramPacket = DatagramPacket(data, size, multicastGroup, multicastPort)
                // パケットをマルチキャストソケットに送信
                multicastSocket.send(datagramPacket)
                // ソケットを閉じる
                multicastSocket.close()

            } catch (e: Exception) {
                Log.i("TransceiverApp", "Exception Occurred! In Sending Data")
                // 例外が発生した場合、スタックトレースを出力する
                e.printStackTrace()
            }
        }
        // スレッドを開始する
        thread.start()
    }

    // 音声データを受信して再生するメソッド
    private fun startReceivingAudio() {
        // 新しいスレッドを作成
        val thread = Thread {
            try {
                // 音声再生用のAudioTrackオブジェクトを作成
                val bufferSize = AudioTrack.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_OUT_MONO, audioEncoding)
                val audioTrack = AudioTrack.Builder()
                    .setAudioAttributes(AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build())
                    .setAudioFormat(AudioFormat.Builder()
                        .setEncoding(audioEncoding)
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build())
                    .setBufferSizeInBytes(bufferSize)
                    .build()

                // 音声再生を開始
                audioTrack.play()

                // マルチキャストソケットを作成
                val multicastSocket = MulticastSocket(multicastPort)
                // マルチキャストグループに参加
                val multicastGroup = InetAddress.getByName(multicastGroupAddress)
                multicastSocket.joinGroup(multicastGroup)

                // 受信データを格納するバッファを作成（1024バイト）
                val buffer = ByteArray(1024)

                // データを受信し続ける
                while (true) {
                    // データを受信
                    val datagramPacket = DatagramPacket(buffer, buffer.size)
                    multicastSocket.receive(datagramPacket)

                    // 受信したデータを再生
                    audioTrack.write(datagramPacket.data, 0, datagramPacket.length)
                }
            } catch (e: Exception) {
                Log.i("TransceiverApp", "Exception Occurred! In Receiving Data")
                // 例外が発生した場合、スタックトレースを出力する
                e.printStackTrace()
            }
        }
        // スレッドを開始する
        thread.start()
    }
}