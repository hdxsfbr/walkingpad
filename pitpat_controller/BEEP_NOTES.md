# Power-on beep notes

From APK reversing, app exposes:
- controlBuzzerSwitch(toggle, type)
- controlSpeaker(powerOnStatus, voicePromptsStatus)
- getSpeakerStatus()

Recovered packet family:
- Query speaker status: `6B 05 9F 9A 43`
- Set speaker config: `6B 07 9E <powerOnStatus> <voicePromptsStatus> <xor> 43`
- XOR is over bytes 1..4, final byte `43`

Likely values:
- `powerOnStatus=0` => disable startup sound
- `voicePromptsStatus=0` => disable voice prompts

Need next:
1. locate exact app call path for sound settings UI, or
2. test whether these packets go over FF01 directly on PITPAT-T01, or via another wrapper/channel.
