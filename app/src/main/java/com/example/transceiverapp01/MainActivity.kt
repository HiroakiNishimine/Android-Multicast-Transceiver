// UDPマルチキャストを利用したトランシーバーアプリ
// このコードは簡単な例であり、実際のトランシーバーアプリではオーディオの送受信やUIの改善、エラー処理など、さまざまな機能を追加する必要があります。
// また、マルチキャスト通信に関連するパーミッション（INTERNETとACCESS_NETWORK_STATE）がAndroidマニフェストファイルに追加されていることを確認してください。

package com.example.transceiverapp01
import android.os.Bundle
import android.os.StrictMode
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import java.net.DatagramPacket
import java.net.InetAddress
import java.net.MulticastSocket
import android.util.Log
import android.view.View
import android.widget.TextView

class MainActivity : AppCompatActivity() {
    private lateinit var messageTextView: TextView
    private val multicastGroupAddress = "224.0.0.1" // マルチキャストグループのアドレス
//    private val multicastGroupAddress = "239.255.255.250"
    private val multicastPort = 12345               // マルチキャスト用のポート番号

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        messageTextView = findViewById(R.id.messageTextView)

        // すべてのスレッドポリシーを許可する設定
        val policy = StrictMode.ThreadPolicy.Builder().permitAll().build()
        StrictMode.setThreadPolicy(policy)

        // 送信ボタンを押したときの処理
        val sendButton: Button = findViewById(R.id.send_button)
        sendButton.setOnClickListener {
            sendData("Hello from my device")
            Log.i("TransceiverApp", "Data Send!")
            messageTextView.text = "Hello there!!"
        }

        // 受信処理を開始
        startReceivingData()
    }

    // データを送信するメソッド
    private fun sendData(message: String) {
        // 新しいスレッドを作成
        val thread = Thread {
            try {
                // マルチキャストソケットを作成する
                val multicastSocket = MulticastSocket()
                // マルチキャストグループのアドレスを取得する
                val multicastGroup = InetAddress.getByName(multicastGroupAddress)
                // メッセージをバイト配列に変換する
                val data = message.toByteArray()
                // 送信用のデータグラムパケットを作成する
                val packet = DatagramPacket(data, data.size, multicastGroup, multicastPort)
                // マルチキャストソケットでデータグラムパケットを送信する
                multicastSocket.send(packet)
                // マルチキャストソケットを閉じる
                multicastSocket.close()
            } catch (e: Exception) {
                Log.i("TransceiverApp", "Exception Occurred!")
                // 例外が発生した場合、スタックトレースを出力する
                e.printStackTrace()
            }
        }
        // スレッドを開始する
        thread.start()
    }

    // データを受信するメソッド
    private fun startReceivingData() {
        // 新しいスレッドを作成
        val thread = Thread {
            try {
                // マルチキャストソケットを作成し、指定されたポート番号をバインドする
                val multicastSocket = MulticastSocket(multicastPort)
                // マルチキャストグループのアドレスを取得する
                val multicastGroup = InetAddress.getByName(multicastGroupAddress)
                // マルチキャストグループに参加する
                multicastSocket.joinGroup(multicastGroup)

                // 受信データを格納するバッファを作成する（1024バイト）
                val buffer = ByteArray(1024)
                // 無限ループでデータを受信し続ける
                while (true) {
                    // 受信用のデータグラムパケットを作成する
                    val packet = DatagramPacket(buffer, buffer.size)
                    // マルチキャストソケットからデータを受信する
                    multicastSocket.receive(packet)
                    // 受信データを文字列に変換する
                    val receivedData = String(packet.data, 0, packet.length)
                    // 受信データをコンソールに出力する
                    Log.i("TransceiverApp", "Received data: $receivedData")
                    // 画面に受信情報を出力
                    runOnUiThread {
                        messageTextView.setText("Received data: $receivedData")
                    }

                }
            } catch (e: Exception) {
                Log.i("TransceiverApp", "Exception Occurred! In ReceivingData")
                // 例外が発生した場合、スタックトレースを出力する
                e.printStackTrace()
            }
        }
        // スレッドを開始する
        thread.start()
    }
}

