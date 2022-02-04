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
        # todo 目前拿到的数据都是None，需要检查
        # old_img = cv2.imdecode(np_data, cv2.IMREAD_UNCHANGED)
        old_img = cv2.imdecode(np_data, 1)
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


if __name__ == '__main__':
    see(None)
