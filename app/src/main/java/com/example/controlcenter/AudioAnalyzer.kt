package com.example.controlcenter

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioPlaybackCaptureConfiguration
import android.media.AudioRecord
import android.media.projection.MediaProjection
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import kotlin.math.sqrt

class AudioAnalyzer(private val context: Context) {
    
    companion object {
        private const val TAG = "AudioAnalyzer"
        private const val SAMPLE_RATE = 44100
        private const val FFT_SIZE = 2048
        private const val BASS_LOW_HZ = 60.0
        private const val BASS_HIGH_HZ = 250.0
        private const val SUB_BASS_LOW_HZ = 20.0
        private const val SUB_BASS_HIGH_HZ = 60.0
    }
    
    private var audioRecord: AudioRecord? = null
    @Volatile
    private var isRecording = false
    private var recordingThread: Thread? = null
    private var mediaProjection: MediaProjection? = null
    
    @Volatile
    private var onBassDetectedListener: ((bassLevel: Float, subBassLevel: Float) -> Unit)? = null
    
    fun setOnBassDetectedListener(listener: (bassLevel: Float, subBassLevel: Float) -> Unit) {
        onBassDetectedListener = listener
    }
    
    fun hasRecordPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }
    
    fun start(mediaProjection: MediaProjection? = null) {
        if (isRecording) {
            Log.w(TAG, "Already recording")
            return
        }
        
        if (!hasRecordPermission()) {
            Log.e(TAG, "RECORD_AUDIO permission not granted")
            return
        }
        
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            Log.e(TAG, "AudioPlaybackCapture requires Android 10+")
            return
        }
        
        this.mediaProjection = mediaProjection
        
        try {
            val audioFormat = AudioFormat.Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setSampleRate(SAMPLE_RATE)
                .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                .build()
            
            val bufferSize = AudioRecord.getMinBufferSize(
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            ).coerceAtLeast(FFT_SIZE * 2)
            
            if (mediaProjection != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val config = AudioPlaybackCaptureConfiguration.Builder(mediaProjection)
                    .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
                    .addMatchingUsage(AudioAttributes.USAGE_GAME)
                    .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN)
                    .build()
                
                audioRecord = AudioRecord.Builder()
                    .setAudioPlaybackCaptureConfig(config)
                    .setAudioFormat(audioFormat)
                    .setBufferSizeInBytes(bufferSize)
                    .build()
            } else {
                audioRecord = AudioRecord(
                    android.media.MediaRecorder.AudioSource.MIC,
                    SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    bufferSize
                )
            }
            
            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "AudioRecord initialization failed")
                return
            }
            
            audioRecord?.startRecording()
            isRecording = true
            
            startProcessingThread()
            
            Log.d(TAG, "Audio analysis started")
        } catch (e: Exception) {
            Log.e(TAG, "Error starting audio analysis", e)
            stop()
        }
    }
    
    private fun startProcessingThread() {
        recordingThread = Thread {
            val buffer = ShortArray(FFT_SIZE)
            val fftBuffer = DoubleArray(FFT_SIZE * 2)
            
            while (isRecording) {
                try {
                    val read = audioRecord?.read(buffer, 0, FFT_SIZE) ?: 0
                    
                    if (read > 0 && isRecording) {
                        for (i in 0 until FFT_SIZE) {
                            fftBuffer[2 * i] = if (i < read) buffer[i].toDouble() else 0.0
                            fftBuffer[2 * i + 1] = 0.0
                        }
                        
                        performFFT(fftBuffer)
                        
                        val bassLevel = extractBassEnergy(fftBuffer, BASS_LOW_HZ, BASS_HIGH_HZ)
                        val subBassLevel = extractBassEnergy(fftBuffer, SUB_BASS_LOW_HZ, SUB_BASS_HIGH_HZ)
                        
                        val listener = onBassDetectedListener
                        if (listener != null && isRecording) {
                            listener.invoke(bassLevel, subBassLevel)
                        }
                    }
                } catch (e: InterruptedException) {
                    break
                } catch (e: Exception) {
                    if (isRecording) {
                        Log.e(TAG, "Error in processing thread", e)
                    }
                }
            }
        }
        recordingThread?.priority = Thread.MAX_PRIORITY
        recordingThread?.start()
    }
    
    private fun performFFT(data: DoubleArray) {
        val n = data.size / 2
        
        var j = 0
        for (i in 0 until n) {
            if (i < j) {
                var temp = data[2 * i]
                data[2 * i] = data[2 * j]
                data[2 * j] = temp
                temp = data[2 * i + 1]
                data[2 * i + 1] = data[2 * j + 1]
                data[2 * j + 1] = temp
            }
            var k = n / 2
            while (k <= j) {
                j -= k
                k /= 2
            }
            j += k
        }
        
        var length = 2
        while (length <= n) {
            val halfLength = length / 2
            val angle = -2.0 * Math.PI / length
            
            val wReal = Math.cos(angle)
            val wImag = Math.sin(angle)
            
            for (i in 0 until n step length) {
                var wnReal = 1.0
                var wnImag = 0.0
                
                for (j in 0 until halfLength) {
                    val evenIdx = (i + j) * 2
                    val oddIdx = (i + j + halfLength) * 2
                    
                    val evenReal = data[evenIdx]
                    val evenImag = data[evenIdx + 1]
                    val oddReal = data[oddIdx]
                    val oddImag = data[oddIdx + 1]
                    
                    val tReal = wnReal * oddReal - wnImag * oddImag
                    val tImag = wnReal * oddImag + wnImag * oddReal
                    
                    data[evenIdx] = evenReal + tReal
                    data[evenIdx + 1] = evenImag + tImag
                    data[oddIdx] = evenReal - tReal
                    data[oddIdx + 1] = evenImag - tImag
                    
                    val tempReal = wnReal * wReal - wnImag * wImag
                    wnImag = wnReal * wImag + wnImag * wReal
                    wnReal = tempReal
                }
            }
            length *= 2
        }
    }
    
    private fun extractBassEnergy(fftData: DoubleArray, lowHz: Double, highHz: Double): Float {
        val binSize = SAMPLE_RATE.toDouble() / FFT_SIZE
        val lowBin = (lowHz / binSize).toInt().coerceAtLeast(0)
        val highBin = (highHz / binSize).toInt().coerceAtMost(FFT_SIZE / 2 - 1)
        
        if (lowBin >= highBin) return 0f
        
        var energy = 0.0
        for (i in lowBin..highBin) {
            val idx = i * 2
            if (idx + 1 < fftData.size) {
                val real = fftData[idx]
                val imag = fftData[idx + 1]
                val magnitude = sqrt(real * real + imag * imag)
                energy += magnitude
            }
        }
        
        energy /= (highBin - lowBin + 1)
        
        val normalizedEnergy = (energy / 5000.0).coerceIn(0.0, 1.0)
        
        return normalizedEnergy.toFloat()
    }
    
    fun stop() {
        if (!isRecording) return
        
        isRecording = false
        onBassDetectedListener = null
        
        try {
            recordingThread?.interrupt()
            recordingThread?.join(1000)
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping recording thread", e)
        }
        recordingThread = null
        
        try {
            audioRecord?.stop()
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping audio record", e)
        }
        
        try {
            audioRecord?.release()
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing audio record", e)
        }
        audioRecord = null
        
        mediaProjection = null
        
        Log.d(TAG, "Audio analysis stopped")
    }
    
    fun isAnalyzing(): Boolean = isRecording
}
