#!/usr/bin/env python
# --*-- coding:utf-8 --*--
# import cv2
import base64
import numpy as np
from brain import Brain
# from input.deal_input import deal_text

cat_brain = Brain()


def see_rgb(str_r, str_g, str_b, str_a):
    """
    模拟看见的过程(先尝试纯粹使用原始数据,不做任何加工)
    接收 RGBA 格式的数据
    """
    react = 0
    try:
        decode_r = base64.b64decode(str_r)
        np_r = np.fromstring(decode_r, np.uint8)  # (307200,) = 640*480
        np_r = np_r.reshape(640, 480)
        print("find r:" + str(np_r.shape))

        decode_g = base64.b64decode(str_g)
        np_g = np.fromstring(decode_g, np.uint8)
        np_g = np_g.reshape(640, 480)
        print("find g:" + str(np_g.shape))

        decode_b = base64.b64decode(str_b)
        np_b = np.fromstring(decode_b, np.uint8)
        np_b = np_b.reshape(640, 480)
        print("find b:" + str(np_b.shape))

        decode_a = base64.b64decode(str_a)
        np_a = np.fromstring(decode_a, np.uint8)
        np_a = np_a.reshape(640, 480)
        print("find a:" + str(np_a.shape))

        np_array = np.array([np_r, np_g, np_b, np_a])
        react, attention = cat_brain.play(np_array)
        # attention 暂不使用
        print(react, attention)
    except Exception as e:
        print(e)
    return react


def see_rgba(str_rgba):
    """
    模拟看见的过程(先尝试纯粹使用原始数据,不做任何加工)
    接收 RGBA 格式的数据
    """
    react = 10
    try:
        decode_r = base64.b64decode(str_rgba)
        np_array = np.fromstring(decode_r, np.uint8)  # (307200,) = 640*480
        np_array = np_array.reshape(640, 480, 4)
        print("find rgba: " + str(np_array.shape))
        react, attention = cat_brain.play(np_array)
        # attention 暂不使用
        print("get react: " + str(react) + ", attention: " + str(attention))
    except Exception as e:
        print(e)
    return react


if __name__ == '__main__':
    see_rgba(None)
