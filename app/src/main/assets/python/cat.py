#!/usr/bin/env python
# --*-- coding:utf-8 --*--
import cv2
import base64
import numpy as np
from input.deal_input import deal_text


def see(str_data):
    """
    模拟看的过程
    """
    try:
        decode_data = base64.b64decode(str_data)
        np_data = np.fromstring(decode_data, np.uint8)
        old_img = cv2.imdecode(np_data, cv2.IMREAD_UNCHANGED)
        img = cv2.resize(old_img, (96, 160), interpolation=cv2.INTER_NEAREST)
        img = img.astype(np.float32)
        img /= 255.0
    except Exception as e:
        print(e)
    # todo 输入数据到 brain
    print("yes I see a image ", img.shape)
    return 1


if __name__ == '__main__':
    see(None)
