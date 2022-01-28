import cv2
import numpy as np

"""
统一数据存储 + 思考模块

使用3d网格代表大脑网络

只接受两种输入: 视频 + 音频
"""


def frame2cell():
    """
    todo 模拟信号进入神经网络的过程
    """
    pass


class Brain():
    """
    大脑类
    """

    cell = np.zeros(shape=(1024, 512, 10))

    def __init__(self) -> None:
        pass

    def video2memory(self, cap):
        """
        将任意形式的媒体捕捉转换为array
        """
        while(cap.isOpened()):
            ret, frame = cap.read()
            if ret==True:
                # todo 这里写入大脑array
                # out.write(frame)
                frame2cell(frame)
                cv2.imshow('frame', frame)
                if cv2.waitKey(1) == ord('q'):
                    break
            else:
                break
    

if __name__=="__main__":
    cap = cv2.VideoCapture(0)
    codec = cv2.VideoWriter_fourcc(*'MJPG')
    fps = 20.0
    frameSize = (640, 480)
    # out = cv2.VideoWriter('video.avi', codec, fps, frameSize)
    # print("按键Q-结束视频录制")
    br = Brain()
    br.video2memory(cap)
    cap.release()
    # out.release()
    cv2.destroyAllWindows()
