# import dill
import random
import numpy as np
import pandas as pd
from os.path import dirname, join
from datetime import datetime
from scipy.stats import entropy
from scipy.spatial import distance as d
"""
小猫的大脑

模仿记忆金属的神经网络
先不要参考任何现有框架(容易被带偏)

1.构建足够多的神经元
2.神经元的连接非固定(取决于进入的信号)
3.根据信号的强度形成记忆
4.输入使用强化学习的形式

基本原理:
小猫大脑的活动实际是多任务强化学习的结果
pip install dill scipy
"""
def load_mem():
    try:
        # with open(join(dirname(__file__), "memory.pkl"), "rb") as file:
        #     memories = dill.load(file)
        df_mem = pd.read_pickle(join(dirname(__file__), "memory.pk"))
        df_mem.drop("index", axis=1, inplace=True, errors="ignore")
    except Exception as e:
        print(e)
        df_mem = None
    return df_mem


def save_mem(df_mem):
    try:
        # with open(join(dirname(__file__), 'memory.pkl'), 'wb') as file:
        #     dill.dump(memories, file)
        df_mem.to_pickle(join(dirname(__file__), "memory.pk"))
    except Exception as e:
        print(e)


def cal_reward(start_time, gain_entropy):
    """
    计算奖励: = (互动时间) * 交互次数 + 信息新颖度
    """
    total_time = (datetime.now() - start_time).total_seconds()
    print("cal reward total time: " + str(total_time) + " and gain entropy: " + str(gain_entropy))
    if gain_entropy is None:
        gain_entropy = 0.001
    reward_score = total_time * gain_entropy / 10
    reward_score = 0.1 if reward_score < 0.1 else reward_score
    return reward_score


class Brain:
    start_time = datetime.now()
    attention = 1       # 注意力
    frame_entropy = 0   # 信息熵
    prev_index = 0    # 上一次scence的index, 用于计算reward
    prev_entropy = None
    express = 12    # 小猫的表达能力
    """
    记忆的结构: 场景, 反馈, 奖励
    memories = [{"frames":[np.array(640, 480, 4)], "reacts": [list], "rewards": [list]}]
    场景 frames: 猫的视网膜只有4层,分别对应RGBA
    """

    def __init__(self) -> None:
        self.sence_start = datetime.now()
        # self.retinas = np.zeros((640, 480, 4))
        self.temp_memory = []
        self.memories = load_mem()
        if self.memories is not None:
            self.prev_index = self.memories.last_valid_index()
            self.prev_entropy = np.mean([entropy(frame) for frame in self.memories.tail(1)["frames"].values[0]])

    def _similar(self, np_array):
        js_score = 1
        js_index = 0
        if self.memories is not None:
            try:
                self.memories["js_score"] = self.memories["frames"].apply(lambda x: d.jensenshannon(np_array, np.array(x).reshape(640, 480, 4, -1)))
                self.memories["js_score"] = pd.to_numeric(self.memories["js_score"])
                js_score = self.memories["js_score"].min()
                js_index = self.memories["js_score"].argmin()
            except Exception as e:
                print("cal similar error:" + str(e))
        return js_score, js_index

    def play(self, np_array):
        """
        模拟小猫与世界互动(看的时候主要关注信息差异)
        熵发生变化时,sence开始和结束的标志(熵的变化是固定的感受野?无法感知局部)
        存在奖励时,则可认为一个sence的结束
        """
        np_array = np_array.reshape(640, 480, 4, -1)
        rewards = None
        cat_react = self.express + 1
        src_entropies = []
        attention = self.attention
        src_entropy = np.mean(entropy(np_array))
        # print("get entropy: " + str(src_entropy))
        # todo 信息熵阈值动态设置 目前发现 0.04 比较好
        if abs(src_entropy - self.frame_entropy) < 0.04:
            self.temp_memory.append(np_array)
            self.attention = self.attention / 0.8
            src_entropies.append(src_entropy)
            # 由于小猫的大脑有限,只能记住最近的32帧,超过且信息熵不变,则开始遗忘
            if len(self.temp_memory) > 32:
                # 计算本次reward
                m_ent = np.mean(src_entropies)
                gain = None
                if self.prev_entropy is not None:
                    gain = abs(m_ent - self.prev_entropy)
                if gain is None:
                    gain = m_ent
                self.prev_entropy = m_ent
                rewards = cal_reward(self.sence_start, gain)
                print("reach reward score: " + str(rewards))
                del self.temp_memory[0]
        else:
            print("find new scence: " + str(src_entropy) + " : " + str(self.frame_entropy))
            if rewards is None:
                m_ent = np.mean(src_entropies)
                gain = None
                if self.prev_entropy is not None:
                    gain = abs(m_ent - self.prev_entropy)
                if gain is None:
                    gain = m_ent
                self.prev_entropy = m_ent
                rewards = cal_reward(self.sence_start, gain)
                print("get reward score: " + str(rewards))
            sence = self.remember_or_learn(self.temp_memory, rewards)
            cat_react = self.react(sence)
            # print("get new react:" + str(cat_react))
            attention = self.attention
            self.attention = 1
            self.temp_memory = [np_array]
            self.frame_entropy = src_entropy
            self.sence_start = datetime.now()
        attention = 0 if attention < 0 else attention
        return cat_react, attention
    
    def react(self, sence):
        """
        反馈
        """
        if sence["reacts"] is None:
            action_list = list(range(self.express))
        else:
            action_list = [a for a in range(self.express) if a not in sence["reacts"]]
            action_list = action_list + [react * reward for react, reward in zip(sence["reacts"], sence["rewards"])]
        reaction = random.choice(action_list)
        return reaction

    def remember_or_learn(self, np_array, prev_reward=None):
        """
        模拟小猫学习或记忆(回忆的时候主要关注信息相似性)
        通过js距离衡量信息相似性
        """
        need_save = False
        js_score, js_index = self._similar(np_array)
        if js_score > 0.4:
            sence = pd.Series({"frames": np_array, "reacts": None, "rewards": None})
            if self.memories is not None:
                self.memories = self.memories.append(sence, ignore_index=True)
                js_index = self.memories.last_valid_index()
            else:
                self.memories = pd.DataFrame([sence])
            need_save = True
        else:
            sence = self.memories.iloc[js_index]
        if prev_reward is not None:
            prev_reward_list = None
            if self.memories.iloc[self.prev_index]["rewards"] is not None:
                prev_reward_list = self.memories.iloc[self.prev_index]["rewards"]
            if prev_reward_list is not None:
                prev_reward_list.append(prev_reward)
            else:
                prev_reward_list = [prev_reward]
            self.memories.at[self.prev_index, "rewards"] = prev_reward_list
            need_save = True
        self.prev_index = js_index
        if need_save:
            save_mem(self.memories)
        return sence

    def sleep(self):
        """
        todo 睡眠时,整理记忆
        1.精简删除相似的记忆
        2.演化新的plan
        """
        pass

    def associate():
        """
        todo 联想
        """
        pass
