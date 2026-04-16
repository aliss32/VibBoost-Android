// Bass Frekansı (Hz) Etiketi
        thresholdLabel = TextView(this).apply {
            text = "Hedef Bass Frekansı: ${VibBoostService.targetFrequency.toInt()} Hz"
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 20)
        }

        // Bass Frekansı Kaydıracı (40 Hz - 250 Hz arası)
        thresholdSeekBar = SeekBar(this).apply {
            max = 210 // 40 Hz başlangıç olduğu için (250 - 40 = 210 adım)
            progress = VibBoostService.targetFrequency.toInt() - 40
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    val freq = progress + 40f // 0'ı 40 Hz'e, 210'u 250 Hz'e eşitliyoruz
                    VibBoostService.targetFrequency = freq
                    thresholdLabel.text = "Hedef Bass Frekansı: ${freq.toInt()} Hz"
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })
            setPadding(0, 0, 0, 60)
        }
