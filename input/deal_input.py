from study.study import Study

"""
处理输入包
"""

def deal_text(str):
    """
    处理文本
    """
    st = Study(str)
    st.study()


def deal_video(stream):
    """
    处理媒体
    """
    pass
