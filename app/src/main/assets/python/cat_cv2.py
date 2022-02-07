#!/usr/bin/env python
# --*-- coding:utf-8 --*--
import cv2
import base64
import numpy as np
# from input.deal_input import deal_text


def see(str_data):
    """
    模拟看的过程
    """
    img = None
    try:
        decode_data = base64.b64decode(str_data)
        np_data = np.fromstring(decode_data, np.uint8)
        # np_data = np.fromstring(decode_data, np.float)
        # (307200,) -> (160, 80, 3)
        # np_data = np_data.reshape(3, 160, 80)
        print("find java data:" + str(np_data.shape))
        old_img = cv2.imdecode(np_data, cv2.IMREAD_UNCHANGED)
        print("find img data:" + str(old_img.shape))
        img = cv2.resize(old_img, (80, 160), interpolation=cv2.INTER_NEAREST)
        img = img.astype(np.float32)
        img /= 255.0
    except Exception as e:
        print(e)
    # todo 输入数据到 brain
    if img is not None:
        print("yes I see a image " + str(img.shape))
    return 1


def see_raw(str_y, str_u, str_v):
    """
    模拟看见的过程(先尝试纯粹使用原始数据,不做任何加工)
    接收 YUV格式的数据
    """
    try:
        decode_y = base64.b64decode(str_y)
        np_y = np.fromstring(decode_y, np.uint8)  # (307200,) = 640*480
        np_y = np_y.reshape(640, 480)
        print("find raw data:" + str(np_y.shape))

        decode_u = base64.b64decode(str_u)
        np_u = np.fromstring(decode_u, np.uint8)
        np_u = np_u.reshape(640, 480)

        decode_v = base64.b64decode(str_v)
        np_v = np.fromstring(decode_v, np.uint8)
        np_v = np_v.reshape(640, 480)

    except Exception as e:
        print(e)
    return 1


if __name__ == '__main__':
    see(None)
