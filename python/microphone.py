import time
import numpy as np
import pyaudio
import config


def get_device_index_for(pa, device_name):
    for i in range(pa.get_device_count()):
        print(pa.get_device_info_by_index(i))
    for i in range(pa.get_device_count()):
        device_info = pa.get_device_info_by_index(i)
        if device_info["name"] == device_name:
            return device_info["index"]
    print("Couldn't find device with name '{}'".format(device_name))
    return -1


def start_stream(callback):
    p = pyaudio.PyAudio()
    device_index = get_device_index_for(p, "Soundflower (2ch)")
    frames_per_buffer = int(config.MIC_RATE / config.FPS)
    stream = p.open(format=pyaudio.paInt16,
                    channels=1,
                    rate=config.MIC_RATE,
                    input=True,
                    frames_per_buffer=frames_per_buffer,
                    input_device_index=device_index  # None = default device
                    )
    overflows = 0
    prev_ovf_time = time.time()
    while True:
        try:
            y = np.fromstring(stream.read(frames_per_buffer, exception_on_overflow=False), dtype=np.int16)
            y = y.astype(np.float32)
            stream.read(stream.get_read_available(), exception_on_overflow=False)
            callback(y)
        except IOError:
            overflows += 1
            if time.time() > prev_ovf_time + 1:
                prev_ovf_time = time.time()
                print('Audio buffer has overflowed {} times'.format(overflows))
    stream.stop_stream()
    stream.close()
    p.terminate()
