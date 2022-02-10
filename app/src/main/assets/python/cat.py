#!/usr/bin/env python
# --*-- coding:utf-8 --*--
# import cv2
import base64
import numpy as np
from brain import Brain

cat_brain = Brain()
see_tick = 0
# 调整看的频率, 方便调试
see_frequency = 1


def see_rgba(str_rgba):
    """
    模拟看见的过程(先尝试纯粹使用原始数据,不做任何加工)
    接收 RGBA 格式的数据
    """
    global see_frequency, see_tick
    react = 10
    if see_tick % see_frequency == 0:
        try:
            decode_r = base64.b64decode(str_rgba)
            np_array = np.fromstring(decode_r, np.uint8)  # (307200,) = 640*480
            np_array = np_array.reshape(640, 480, 4)
            # print("find rgba: " + str(np_array.shape))
            react, attention = cat_brain.play(np_array)
            # attention 暂不使用
            if react < 10:
                print("get react: " + str(react) + ", attention: " + str(attention))
        except Exception as e:
            print(e)
    see_tick += 1
    if see_tick >= 999999:
        see_tick = 0
    return react


if __name__ == '__main__':
    see_rgba(None)
