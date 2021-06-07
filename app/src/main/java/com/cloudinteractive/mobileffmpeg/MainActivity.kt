package com.cloudinteractive.mobileffmpeg

import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.text.method.ScrollingMovementMethod
import android.util.Log
import android.view.View.GONE
import android.view.View.VISIBLE
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.arthenica.mobileffmpeg.Config
import com.arthenica.mobileffmpeg.Config.RETURN_CODE_SUCCESS
import com.arthenica.mobileffmpeg.FFmpeg
import com.arthenica.mobileffmpeg.FFprobe
import com.cloudinteractive.mobileffmpeg.databinding.ActivityMainBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {

    private final val TAG = "Jimi"

    private val binding by viewBinding(ActivityMainBinding::inflate)

    private var inputVideoFilePath: String? = null
    private var videoFileName: String? = null

    var endTime: Long? = null

    private val getContent =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            Log.e(TAG, uri.toString())


            uri?.let {

                val projection = arrayOf("_data", MediaStore.MediaColumns.DISPLAY_NAME)

                val documentId = DocumentsContract.getDocumentId(it)
                val selectionArgs = arrayOf(DocumentsContract.getDocumentId(it).split(":")[1])

                videoFileName = null
                inputVideoFilePath = null

                val cursor = this.contentResolver.query(
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                    projection,
                    "_id=?",
                    selectionArgs,
                    null
                )

                if (cursor != null) {
                    cursor.moveToFirst()
                    inputVideoFilePath =
                        cursor.getString(cursor.getColumnIndex(MediaStore.MediaColumns.DATA))
                    videoFileName =
                        cursor.getString(cursor.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME))

                    Log.e(TAG, "$inputVideoFilePath  $videoFileName")
                    cursor.close()

                    lifecycleScope.launch(Dispatchers.IO) {

                        val output: String

                        if (FFprobe.execute("-i $inputVideoFilePath") == RETURN_CODE_SUCCESS) {
                            output = Config.getLastCommandOutput()

                            binding.tvProbe.text = output
                            // 找出 Duration
                            val items = output.split("\n")
                            val durationString = items.find { item -> item.contains("Duration") }
                                ?.split(",")
                                ?.get(0)
                                ?.trim()
                                ?.split(" ")
                                ?.get(1)

                            durationString?.let { duration ->
                                Log.e(TAG, duration)
                                val splits = duration.split(":")

                                if (splits.size != 3)
                                    throw IllegalStateException("duration error")
                                endTime =
                                    splits[0].toLong() * 60 * 60 + splits[1].toLong() * 60 + splits[2].split(
                                        "."
                                    )[0].toLong()

                                Log.e(TAG, endTime.toString())


                            }

//                        val duration = items.firstOrNull { item -> item.contains("Duration") }

//                        Log.e(TAG, duration!!)
                        } else {
                            endTime = null

                            output = Config.getLastCommandOutput()


//                        Toast.makeText(this@MainActivity, "FFprobe fail !", Toast.LENGTH_LONG).show()
                        }

                        withContext(Dispatchers.Main) {
                            binding.tvProbe.text = output
                            initSetting(endTime)
                        }
                    }

                }
            }


        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)


        if (ContextCompat.checkSelfPermission(
                this,
                android.Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this, arrayOf(
                    android.Manifest.permission.WRITE_EXTERNAL_STORAGE
                ), 0
            )
        }

        binding.rsDuration.isEnabled = false

        binding.tvProbe.movementMethod = ScrollingMovementMethod()

        binding.btnPick.setOnClickListener {
            getContent.launch("video/*")
        }

        binding.btnRun.isEnabled = false
        binding.btnRun.setOnClickListener {
            binding.progressBar.visibility = VISIBLE
            lifecycleScope.launch(Dispatchers.IO) {

                val newFileName = newFileName(videoFileName!!)

                val newFile = File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                    newFileName
                )

                Log.e(TAG, "start: ${binding.rsDuration.values[0]}")
                Log.e(TAG, "end: ${binding.rsDuration.values[1]}")

                val command = "-i $inputVideoFilePath -ss ${
                    (1000 * binding.rsDuration.values[0]).toLong().toFFMpegFormat()
                } -to ${
                    (1000 * binding.rsDuration.values[1]).toLong().toFFMpegFormat()
                } -acodec copy -vcodec copy ${newFile.absolutePath}"
                    .also { Log.e(TAG, it) }

                FFmpeg.execute(command)

                withContext(Dispatchers.Main) {
                    binding.progressBar.visibility = GONE
                    Toast.makeText(this@MainActivity, "$newFileName 完成", Toast.LENGTH_LONG).show()
                }
            }
        }
    }


    private fun initSetting(endTime: Long?) {
        if (endTime == null) {
            binding.rsDuration.isEnabled = false

            binding.btnRun.isEnabled = false
        } else {
            binding.rsDuration.isEnabled = true
            binding.rsDuration.valueFrom = 0.0f
            binding.rsDuration.valueTo = endTime.toFloat()
            binding.rsDuration.invalidate()
            binding.rsDuration.setValues(0.0f, endTime.toFloat())

            binding.btnRun.isEnabled = true
        }

    }


    private fun newFileName(fileName: String): String {
        val dotIndex = fileName.lastIndexOf(".")

        if (dotIndex == -1) {
            return "$fileName[${binding.rsDuration.values[0].toInt()}-${binding.rsDuration.values[1].toInt()}]"
        } else {
            return "${
                fileName.substring(
                    0,
                    dotIndex
                )
            }[${binding.rsDuration.values[0].toInt()}-${binding.rsDuration.values[1].toInt()}]" + fileName.substring(
                dotIndex
            )
        }
    }
}

fun Long.toFFMpegFormat(): String {
    return String.format(
        "%02d:%02d:%02d.00",
        TimeUnit.MILLISECONDS.toHours(this),
        TimeUnit.MILLISECONDS.toMinutes(this) - TimeUnit.MINUTES.toMinutes(
            TimeUnit.MILLISECONDS.toHours(
                this
            )
        ),
        TimeUnit.MILLISECONDS.toSeconds(this) - TimeUnit.MINUTES.toSeconds(
            TimeUnit.MILLISECONDS.toMinutes(
                this
            )
        )
    )
}